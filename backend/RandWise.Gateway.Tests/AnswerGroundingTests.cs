using RandWise.Gateway;

namespace RandWise.Gateway.Tests;

public class AnswerGroundingTests
{
    private static CoachRequest Request(string question = "How much can I spend?") => new(
        question,
        new CoachFacts(
            Currency: "ZAR", Today: "2026-10-20", DaysUntilPayday: 3,
            LiquidBalanceMinor: 1_808_800, SafeToSpendMinor: 1_296_788, SafeToSpendPerDayMinor: 432_262,
            SafetyBufferMinor: 100_000, MonthlyIncomeMinor: 2_850_000, CycleSpendMinor: 1_868_600, ProjectedCycleSpendMinor: 2_237_300,
            Categories: [new CategoryFact("Groceries", 328_000, 450_000, 410_000, "ON_TRACK")],
            Bills: [new BillFact("Health", 312_000, 2, false)],
            ForecastLowestMinor: 1_385_937, ForecastLowestInDays: 2, ForecastNegativeInDays: null,
            VolatilityScore: 94, PostPaydaySpikePercent: "278.0%", FeeTrapCount: 1, FeeTrapTotalMinor: 9_500,
            EmergencyMonthsCovered: "1.20", EmergencyTargetMonths: 3,
            Debts: [new DebtFact("Debt 1", 820_000, "20.7%", 24_600)],
            DetectedInsights: ["Fee traps detected: 1 bank penalty fee cost you R 95,00 in 90 days."]),
        []);

    [Theory]
    [InlineData("R 1 234,56", 123_456)]
    [InlineData("R1,234.56", 123_456)]
    [InlineData("R1500", 150_000)]
    [InlineData("R 12 968", 1_296_800)]
    [InlineData("R 12 968,00", 1_296_800)]
    [InlineData("-R 50", -5_000)]
    public void Parses_south_african_and_international_formats(string text, long expected)
    {
        Assert.True(AnswerGrounding.TryParseCents(text, out var cents));
        Assert.Equal(expected, cents);
    }

    [Fact]
    public void Extracts_every_amount_in_a_sentence()
    {
        var found = AnswerGrounding.ExtractCents("You have R 12 968 to spend, about R4 323 a day, after R3,120.00 for medical aid.");
        Assert.Equal([1_296_800L, 432_300L, 312_000L], found);
    }

    [Fact]
    public void Answer_quoting_facts_is_grounded_to_the_nearest_rand()
    {
        var answer = "You can safely spend R 12 968 (about R 4 323 a day). Your medical aid of R 3 120 is still due, and groceries are at R 3 280 of R 4 500.";
        Assert.Empty(AnswerGrounding.Ungrounded(answer, [], Request()));
    }

    [Fact]
    public void Invented_or_miscalculated_amounts_are_caught()
    {
        var answer = "You can spend R 15 000 this week and should save R 2 500.";
        var bad = AnswerGrounding.Ungrounded(answer, ["Move R 2 500 to savings"], Request());
        Assert.Equal([1_500_000L, 250_000L], bad);
    }

    [Fact]
    public void Amounts_from_the_users_question_are_allowed()
    {
        var req = Request("Can I afford R1500 for shoes?");
        // R1 500 echoes the question; R11 467,88 is safe-to-spend minus that purchase.
        Assert.Empty(AnswerGrounding.Ungrounded("Yes — R1 500 fits, leaving R 11 467,88 safe to spend.", [], req));
    }

    [Fact]
    public void Amounts_quoted_in_detected_insights_are_allowed()
    {
        Assert.Empty(AnswerGrounding.Ungrounded("That R95 penalty fee is avoidable.", [], Request()));
    }
}
