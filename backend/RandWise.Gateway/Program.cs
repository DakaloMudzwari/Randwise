using System.Threading.RateLimiting;
using Microsoft.AspNetCore.HttpOverrides;
using RandWise.Gateway;

var builder = WebApplication.CreateBuilder(args);

var gemini = builder.Configuration.GetSection("Gemini").Get<GeminiOptions>() ?? new GeminiOptions();
// Prefer the conventional env var so the key never sits in a config file.
gemini.ApiKey = Environment.GetEnvironmentVariable("GEMINI_API_KEY") is { Length: > 0 } envKey ? envKey : gemini.ApiKey;
builder.Services.AddSingleton(gemini);
// Per Gemini call; a check-up with one grounding retry can take two.
builder.Services.AddHttpClient<GeminiCoachService>(c => c.Timeout = TimeSpan.FromSeconds(50));

// Behind Cloud Run / Container Apps / a reverse proxy every request arrives from the proxy's address, so the
// per-IP limit would throttle all users as one. Set RANDWISE_TRUST_PROXY=true there to read the client IP the
// proxy appends to X-Forwarded-For (only the last hop, so clients can't spoof their way past the limit).
// Leave it off when Kestrel faces the internet directly.
var trustProxy = string.Equals(Environment.GetEnvironmentVariable("RANDWISE_TRUST_PROXY"), "true", StringComparison.OrdinalIgnoreCase);
builder.Services.Configure<ForwardedHeadersOptions>(o =>
{
    o.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    o.ForwardLimit = 1;
    o.KnownIPNetworks.Clear();
    o.KnownProxies.Clear();
});

// Spend ceiling across all users: the client key ships in the APK, so per-IP limits alone can't cap the Gemini bill.
var dailyCap = int.TryParse(Environment.GetEnvironmentVariable("RANDWISE_DAILY_REQUEST_CAP"), out var cap) && cap > 0
    ? cap
    : builder.Configuration.GetValue("Gateway:DailyRequestCap", 5000);

builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    o.GlobalLimiter = PartitionedRateLimiter.CreateChained(
        PartitionedRateLimiter.Create<HttpContext, string>(ctx => !ctx.Request.Path.StartsWithSegments("/api")
            ? RateLimitPartition.GetNoLimiter("other")
            : RateLimitPartition.GetFixedWindowLimiter(
                ctx.Connection.RemoteIpAddress?.ToString() ?? "anon",
                _ => new FixedWindowRateLimiterOptions { PermitLimit = 20, Window = TimeSpan.FromMinutes(1) })),
        PartitionedRateLimiter.Create<HttpContext, string>(ctx => !ctx.Request.Path.StartsWithSegments("/api")
            ? RateLimitPartition.GetNoLimiter("other")
            : RateLimitPartition.GetFixedWindowLimiter(
                "all-clients",
                _ => new FixedWindowRateLimiterOptions { PermitLimit = dailyCap, Window = TimeSpan.FromDays(1) })));
});
builder.Services.Configure<Microsoft.AspNetCore.Server.Kestrel.Core.KestrelServerOptions>(o => o.Limits.MaxRequestBodySize = 64 * 1024);

// Shared client key: blocks casual abuse of your Gemini quota. It ships inside the APK, so treat it as
// a speed bump, not a secret — add Play Integrity / Firebase App Check attestation before scaling.
var clientKey = Environment.GetEnvironmentVariable("RANDWISE_CLIENT_KEY") ?? builder.Configuration["Gateway:ClientKey"] ?? "";

var app = builder.Build();
if (trustProxy) app.UseForwardedHeaders();

app.Use(async (ctx, next) =>
{
    if (clientKey.Length > 0 && ctx.Request.Path.StartsWithSegments("/api"))
    {
        var supplied = ctx.Request.Headers["X-RandWise-Client-Key"].ToString();
        var ok = System.Security.Cryptography.CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.UTF8.GetBytes(supplied), System.Text.Encoding.UTF8.GetBytes(clientKey));
        if (!ok)
        {
            ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }
    }
    await next();
});
// After the key check, so unauthenticated noise can't use up the daily cap.
app.UseRateLimiter();

app.MapGet("/health", () => Results.Ok(new { status = "ok", geminiConfigured = gemini.ApiKey.Length > 0, clientKeyRequired = clientKey.Length > 0, model = gemini.Model }));

app.MapPost("/api/coach", async (CoachRequest request, GeminiCoachService coach, CancellationToken ct) =>
{
    if (string.IsNullOrWhiteSpace(request.Question) || request.Facts is null)
        return Results.BadRequest(new { error = "question and facts are required" });
    if (gemini.ApiKey.Length == 0)
        return Results.Problem("Gemini API key not configured on the gateway", statusCode: StatusCodes.Status503ServiceUnavailable);

    var clean = PayloadSanitizer.Sanitize(request);
    try
    {
        return Results.Ok(await coach.AskAsync(clean, ct));
    }
    catch (Exception e) when (e is HttpRequestException or InvalidOperationException or TaskCanceledException or System.Text.Json.JsonException)
    {
        return Results.Problem("The AI coach is unavailable right now", statusCode: StatusCodes.Status502BadGateway);
    }
});

app.MapPost("/api/checkup", async (CheckupRequest request, GeminiCoachService coach, CancellationToken ct) =>
{
    if (request.Facts is null || request.Facts.Categories is null)
        return Results.BadRequest(new { error = "facts are required" });
    if (gemini.ApiKey.Length == 0)
        return Results.Problem("Gemini API key not configured on the gateway", statusCode: StatusCodes.Status503ServiceUnavailable);

    try
    {
        return Results.Ok(await coach.CheckupAsync(PayloadSanitizer.Sanitize(request), ct));
    }
    catch (Exception e) when (e is HttpRequestException or InvalidOperationException or TaskCanceledException or System.Text.Json.JsonException)
    {
        return Results.Problem("The AI check-up is unavailable right now", statusCode: StatusCodes.Status502BadGateway);
    }
});

app.Run();
