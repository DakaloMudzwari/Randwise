namespace RandWise.Gateway;

// Mirrors app/src/main/java/com/randwise/app/ai/CheckupModels.kt. Amounts are integer cents per month.

public sealed record CategoryTrendFact(
    string Category,
    bool Essential,
    List<long> MonthsMinor,
    long TypicalMinor,
    long? SuggestedLimitMinor,
    long? SuggestedSavingMinor);

public sealed record MerchantFact(string Label, long TotalMinor, int Visits, string? Category);

public sealed record CheckupFacts(
    string Currency,
    string Today,
    int MonthsCovered,
    int DaysOfData,
    long MonthlyIncomeMinor,
    long PlannedSavingsMinor,
    long AvailableForBudgetMinor,
    List<long> MonthlySpendMinor,
    long TypicalSpendMinor,
    long UncategorisedTypicalMinor,
    List<CategoryTrendFact> Categories,
    List<MerchantFact> Merchants,
    int FeeCount,
    long FeeTotalMinor,
    List<string> DetectedInsights);

public sealed record CheckupRequest(CheckupFacts Facts);

public sealed record CheckupNote(string Title, string Body);

public sealed record CheckupBudgetProposal(string Category, long LimitMinor, string Reason);

public sealed record CheckupResponse(
    string Headline,
    string Summary,
    List<CheckupNote> Insights,
    List<CheckupNote> Suggestions,
    List<CheckupBudgetProposal> Budget,
    string Disclaimer);
