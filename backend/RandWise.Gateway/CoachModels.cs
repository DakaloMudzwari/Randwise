namespace RandWise.Gateway;

// Mirrors app/src/main/java/com/randwise/app/ai/CoachModels.kt. Amounts are integer cents.

public sealed record CategoryFact(string Category, long SpentMinor, long? LimitMinor, long? ProjectedMinor, string? Status);

public sealed record BillFact(string Label, long AmountMinor, long DueInDays, bool Paid);

public sealed record DebtFact(string Label, long BalanceMinor, string AprPercent, long MinPaymentMinor);

public sealed record CoachFacts(
    string Currency,
    string Today,
    long DaysUntilPayday,
    long LiquidBalanceMinor,
    long SafeToSpendMinor,
    long SafeToSpendPerDayMinor,
    long SafetyBufferMinor,
    long MonthlyIncomeMinor,
    long CycleSpendMinor,
    long ProjectedCycleSpendMinor,
    List<CategoryFact> Categories,
    List<BillFact> Bills,
    long ForecastLowestMinor,
    long ForecastLowestInDays,
    long? ForecastNegativeInDays,
    int VolatilityScore,
    string PostPaydaySpikePercent,
    int FeeTrapCount,
    long FeeTrapTotalMinor,
    string EmergencyMonthsCovered,
    int EmergencyTargetMonths,
    List<DebtFact> Debts,
    List<string> DetectedInsights);

public sealed record ChatTurn(string Role, string Text);

public sealed record CoachRequest(string Question, CoachFacts Facts, List<ChatTurn>? History);

public sealed record CoachResponse(string Answer, List<string> Highlights, string Disclaimer);
