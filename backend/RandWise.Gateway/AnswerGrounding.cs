using System.Globalization;
using System.Text.RegularExpressions;

namespace RandWise.Gateway;

/// <summary>
/// Hallucination guard for money. Every rand amount the model writes must match a figure in the
/// facts we sent (or one the user typed in their question), to the nearest rand. The model is told
/// never to do its own arithmetic, so an unmatched amount means it invented or miscalculated one.
/// </summary>
public static partial class AnswerGrounding
{
    // "R 1 234,56", "R1,234.56", "R1500", "-R 50", "R 12 968" (with normal or non-breaking spaces).
    [GeneratedRegex(@"-?R\s?-?\d{1,3}(?:[  ,]\d{3})*(?:[.,]\d{1,2})?(?!\d)|-?R\s?-?\d+(?:[.,]\d{1,2})?")]
    private static partial Regex RandAmount();

    /// <summary>Amounts in cents, parsed from South African or international formatting.</summary>
    public static List<long> ExtractCents(string text)
    {
        var result = new List<long>();
        foreach (Match m in RandAmount().Matches(text))
        {
            if (TryParseCents(m.Value, out var cents)) result.Add(cents);
        }
        return result;
    }

    public static bool TryParseCents(string raw, out long cents)
    {
        cents = 0;
        var negative = raw.Contains('-');
        var s = raw.Replace("R", "").Replace("-", "").Replace(" ", "").Replace(" ", "").Trim();
        if (s.Length == 0) return false;
        // The last '.' or ',' followed by 1-2 digits is the decimal mark; other separators group thousands.
        var dec = Math.Max(s.LastIndexOf('.'), s.LastIndexOf(','));
        string whole, frac;
        if (dec >= 0 && s.Length - dec - 1 is 1 or 2)
        {
            whole = s[..dec].Replace(".", "").Replace(",", "");
            frac = s[(dec + 1)..].PadRight(2, '0');
        }
        else
        {
            whole = s.Replace(".", "").Replace(",", "");
            frac = "00";
        }
        if (!long.TryParse(whole.Length == 0 ? "0" : whole, NumberStyles.None, CultureInfo.InvariantCulture, out var w)) return false;
        if (!long.TryParse(frac, NumberStyles.None, CultureInfo.InvariantCulture, out var f)) return false;
        cents = (w * 100 + f) * (negative ? -1 : 1);
        return true;
    }

    /// <summary>Every money figure the model is allowed to quote.</summary>
    public static HashSet<long> AllowedCents(CoachRequest request)
    {
        var f = request.Facts;
        var set = new HashSet<long>
        {
            f.LiquidBalanceMinor, f.SafeToSpendMinor, f.SafeToSpendPerDayMinor, f.SafetyBufferMinor,
            f.MonthlyIncomeMinor, f.CycleSpendMinor, f.ProjectedCycleSpendMinor, f.ForecastLowestMinor, f.FeeTrapTotalMinor,
        };
        foreach (var c in f.Categories)
        {
            set.Add(c.SpentMinor);
            if (c.LimitMinor is { } l) { set.Add(l); set.Add(l - c.SpentMinor); }
            if (c.ProjectedMinor is { } p) set.Add(p);
        }
        foreach (var b in f.Bills) set.Add(b.AmountMinor);
        if (f.Bills.Count > 0) set.Add(f.Bills.Where(b => !b.Paid).Sum(b => b.AmountMinor));
        foreach (var d in f.Debts) { set.Add(d.BalanceMinor); set.Add(d.MinPaymentMinor); }
        // Amounts in the user's own question (e.g. "Can I afford R1500?") and the obvious consequence.
        foreach (var q in ExtractCents(request.Question))
        {
            set.Add(q);
            set.Add(f.SafeToSpendMinor - q);
        }
        // Amounts quoted in the detected-insight sentences we sent.
        foreach (var i in f.DetectedInsights) foreach (var a in ExtractCents(i)) set.Add(a);
        return set;
    }

    /// <summary>Amounts in [answer] that don't match any allowed figure to within one rand.</summary>
    public static List<long> Ungrounded(string answer, IEnumerable<string> highlights, CoachRequest request) =>
        Ungrounded(highlights.Prepend(answer), AllowedCents(request));

    /// <summary>Amounts in [texts] that don't match any [allowed] figure to within one rand.</summary>
    public static List<long> Ungrounded(IEnumerable<string> texts, IEnumerable<long> allowed)
    {
        var ok = allowed.Select(Math.Abs).ToList();
        return texts.SelectMany(ExtractCents).Where(a => !ok.Any(x => Math.Abs(Math.Abs(a) - x) <= 100)).Distinct().ToList();
    }

    /// <summary>
    /// Every money figure a check-up may quote: the facts, the model's own budget limits (the app checks those
    /// separately) and the saving each limit implies against that category's typical month.
    /// </summary>
    public static HashSet<long> AllowedCents(CheckupFacts f, IEnumerable<CheckupBudgetProposal> budget)
    {
        var set = new HashSet<long>
        {
            f.MonthlyIncomeMinor, f.PlannedSavingsMinor, f.AvailableForBudgetMinor, f.TypicalSpendMinor,
            f.UncategorisedTypicalMinor, f.FeeTotalMinor, f.MonthlyIncomeMinor - f.TypicalSpendMinor,
        };
        foreach (var m in f.MonthlySpendMinor) set.Add(m);
        var typicalByName = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);
        foreach (var c in f.Categories)
        {
            typicalByName[c.Category] = c.TypicalMinor;
            set.Add(c.TypicalMinor);
            foreach (var m in c.MonthsMinor) set.Add(m);
            if (c.SuggestedLimitMinor is { } l) set.Add(l);
            if (c.SuggestedSavingMinor is { } s) set.Add(s);
        }
        foreach (var m in f.Merchants) set.Add(m.TotalMinor);
        foreach (var i in f.DetectedInsights) foreach (var a in ExtractCents(i)) set.Add(a);
        foreach (var b in budget)
        {
            set.Add(b.LimitMinor);
            if (typicalByName.TryGetValue(b.Category, out var typical)) set.Add(typical - b.LimitMinor);
        }
        return set;
    }

    /// <summary>All the prose in a check-up, for grounding.</summary>
    public static IEnumerable<string> Texts(CheckupResponse r) =>
        new[] { r.Headline, r.Summary }
            .Concat(r.Insights.SelectMany(n => new[] { n.Title, n.Body }))
            .Concat(r.Suggestions.SelectMany(n => new[] { n.Title, n.Body }))
            .Concat(r.Budget.Select(b => b.Reason));
}
