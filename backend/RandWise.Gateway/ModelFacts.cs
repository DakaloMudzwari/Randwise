using System.Text.Json;
using System.Text.Json.Nodes;

namespace RandWise.Gateway;

/// <summary>
/// The model sees rands, not cents. Asked to convert, language models misplace the decimal ("R2 598 649" for
/// R25 986,49), so every "...Minor" field becomes "...Rands" before the facts are sent. The app and the grounding
/// check keep working in cents.
/// </summary>
public static class ModelFacts
{
    public static string InRands<T>(T facts, JsonSerializerOptions options) =>
        Convert(JsonSerializer.SerializeToNode(facts, options))?.ToJsonString() ?? "{}";

    private static JsonNode? Convert(JsonNode? node) => node switch
    {
        JsonObject o => ConvertObject(o),
        JsonArray a => new JsonArray(a.Select(Convert).ToArray()),
        _ => node?.DeepClone(),
    };

    private static JsonObject ConvertObject(JsonObject o)
    {
        var result = new JsonObject();
        foreach (var (key, value) in o)
        {
            if (key.EndsWith("Minor", StringComparison.Ordinal)) result[key[..^5] + "Rands"] = ToRands(value);
            else result[key] = Convert(value);
        }
        return result;
    }

    private static JsonNode? ToRands(JsonNode? value) => value switch
    {
        JsonValue v when v.TryGetValue<long>(out var cents) => JsonValue.Create(cents / 100m),
        JsonArray a => new JsonArray(a.Select(ToRands).ToArray()),
        _ => value?.DeepClone(),
    };
}
