using System.Text.RegularExpressions;

namespace RandWise.Gateway;

/// <summary>
/// Defence in depth: the app already anonymises facts, but the gateway strips anything that
/// looks like PII again before the payload reaches Gemini, and bounds its size.
/// </summary>
public static partial class PayloadSanitizer
{
    [GeneratedRegex(@"\b\d[\d \-]{7,}\d\b")]
    private static partial Regex LongNumber();

    [GeneratedRegex(@"[\w.+-]+@[\w-]+\.[\w.]+")]
    private static partial Regex Email();

    [GeneratedRegex(@"(\+27|0)[6-8]\d[ -]?\d{3}[ -]?\d{4}")]
    private static partial Regex SaPhone();

    public static string Clean(string? text, int max = 600)
    {
        if (string.IsNullOrWhiteSpace(text)) return "";
        var s = Email().Replace(text, "[email]");
        s = SaPhone().Replace(s, "[phone]");
        s = LongNumber().Replace(s, "[number]");
        return s.Length > max ? s[..max] : s;
    }

    public static CoachRequest Sanitize(CoachRequest r)
    {
        var f = r.Facts;
        var facts = f with
        {
            Currency = Clean(f.Currency, 3),
            Categories = f.Categories.Take(20).Select(c => c with { Category = Clean(c.Category, 40) }).ToList(),
            Bills = f.Bills.Take(30).Select(b => b with { Label = Clean(b.Label, 40) }).ToList(),
            Debts = f.Debts.Take(10).Select(d => d with { Label = Clean(d.Label, 20) }).ToList(),
            DetectedInsights = f.DetectedInsights.Take(10).Select(i => Clean(i, 300)).ToList(),
        };
        var history = (r.History ?? []).TakeLast(6)
            .Select(h => new ChatTurn(h.Role == "user" ? "user" : "model", Clean(h.Text)))
            .ToList();
        return new CoachRequest(Clean(r.Question, 800), facts, history);
    }
}
