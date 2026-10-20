using System.Threading.RateLimiting;
using RandWise.Gateway;

var builder = WebApplication.CreateBuilder(args);

var gemini = builder.Configuration.GetSection("Gemini").Get<GeminiOptions>() ?? new GeminiOptions();
// Prefer the conventional env var so the key never sits in a config file.
gemini.ApiKey = Environment.GetEnvironmentVariable("GEMINI_API_KEY") is { Length: > 0 } envKey ? envKey : gemini.ApiKey;
builder.Services.AddSingleton(gemini);
builder.Services.AddHttpClient<GeminiCoachService>(c => c.Timeout = TimeSpan.FromSeconds(25));

builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    o.AddPolicy("coach", ctx => RateLimitPartition.GetFixedWindowLimiter(
        ctx.Connection.RemoteIpAddress?.ToString() ?? "anon",
        _ => new FixedWindowRateLimiterOptions { PermitLimit = 20, Window = TimeSpan.FromMinutes(1) }));
});
builder.Services.Configure<Microsoft.AspNetCore.Server.Kestrel.Core.KestrelServerOptions>(o => o.Limits.MaxRequestBodySize = 64 * 1024);

var app = builder.Build();
app.UseRateLimiter();

app.MapGet("/health", () => Results.Ok(new { status = "ok", geminiConfigured = gemini.ApiKey.Length > 0, model = gemini.Model }));

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
}).RequireRateLimiting("coach");

app.Run();
