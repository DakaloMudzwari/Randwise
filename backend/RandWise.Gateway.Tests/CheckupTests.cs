using RandWise.Gateway;

namespace RandWise.Gateway.Tests;

public class CheckupTests
{
    private static CheckupFacts Facts(string category = "Takeaways") => new(
        Currency: "ZAR", Today: "2026-10-20", MonthsCovered: 3, DaysOfData: 90,
        MonthlyIncomeMinor: 2_000_000, PlannedSavingsMinor: 200_000, AvailableForBudgetMinor: 1_800_000,
        MonthlySpendMinor: [1_300_000, 1_370_000, 1_380_000], TypicalSpendMinor: 1_370_000, UncategorisedTypicalMinor: 0,
        Categories: [new CategoryTrendFact(category, false, [100_000, 150_000, 200_000], 150_000, 135_000, 15_000)],
        Merchants: [new MerchantFact("MRD", 450_000, 9, category)],
        FeeCount: 0, FeeTotalMinor: 0, DetectedInsights: []);

    private static CheckupResponse Response(string body, long limit = 120_000) => new(
        "You keep R6 300 a month", "Takeaways is climbing.",
        [new CheckupNote("Takeaways is climbing", body)],
        [],
        [new CheckupBudgetProposal("Takeaways", limit, "Cook at home twice a week")],
        "");

    private static List<long> Ungrounded(CheckupResponse r, CheckupFacts f) =>
        AnswerGrounding.Ungrounded(AnswerGrounding.Texts(r), AnswerGrounding.AllowedCents(f, r.Budget));

    [Fact]
    public void Facts_budget_limits_and_implied_savings_are_grounded()
    {
        // R1 000 → R2 000 are months, R1 200 is the model's own limit, R300 is what that limit saves on R1 500.
        var r = Response("From R1 000 to R2 000 a month. A R1 200 cap saves R300; we suggested R1 350 (saves R150).");
        Assert.Empty(Ungrounded(r, Facts()));
    }

    [Fact]
    public void Invented_amounts_are_caught()
    {
        var r = Response("You could save R2 750 a month on takeaways.");
        Assert.Equal([275_000L], Ungrounded(r, Facts()));
    }

    [Fact]
    public void Savings_are_only_allowed_for_categories_that_exist()
    {
        var r = Response("Capping saves R300.") with { Budget = [new CheckupBudgetProposal("Crypto", 120_000, "")] };
        Assert.Equal([30_000L], Ungrounded(r, Facts()));
    }

    [Fact]
    public void Sanitizer_strips_pii_from_labels_and_bounds_lists()
    {
        var f = Facts("Pay john@example.com 0821234567") with
        {
            Merchants = Enumerable.Range(0, 20).Select(i => new MerchantFact("SHOP" + i, 100, 1, null)).ToList(),
        };
        var clean = PayloadSanitizer.Sanitize(new CheckupRequest(f)).Facts;
        Assert.DoesNotContain("@", clean.Categories[0].Category);
        Assert.DoesNotContain("0821234567", clean.Categories[0].Category);
        Assert.Equal(8, clean.Merchants.Count);
    }
}

public class ModelFactsTests
{
    [Fact]
    public void Cents_fields_reach_the_model_as_rands()
    {
        var json = ModelFacts.InRands(new CheckupRequest(new CheckupFacts(
            "ZAR", "2026-10-22", 3, 90, 2_850_000, 300_000, 2_550_000, [2_598_649], 2_598_649, 0,
            [new CategoryTrendFact("Groceries", true, [540_400], 540_400, 540_400, 0)], [], 0, 0, [])).Facts,
            new System.Text.Json.JsonSerializerOptions(System.Text.Json.JsonSerializerDefaults.Web));
        Assert.Contains("\"typicalSpendRands\":25986.49", json);
        Assert.Contains("\"monthsRands\":[5404]", json);
        Assert.DoesNotContain("Minor", json);
        Assert.Contains("\"daysOfData\":90", json); // non-money numbers untouched
    }
}
