using System.Text.Json;

static bool ReachedLoginState(string? state) =>
    state is "LOGIN_SCREEN"
        or "LOGIN_SCREEN_AUTHENTICATOR"
        or "LOGGING_IN"
        or "LOADING"
        or "LOGGED_IN";

static (long Sequence, string? State) ParseHealth(string jsonText)
{
    using var json = JsonDocument.Parse(jsonText);
    var sequence = json.RootElement.TryGetProperty("clientHeartbeatSequence", out var seq)
        && seq.TryGetInt64(out var value)
            ? value
            : -1;
    var state = json.RootElement.TryGetProperty("clientGameState", out var gameState)
        && gameState.ValueKind == JsonValueKind.String
            ? gameState.GetString()
            : null;
    return (sequence, state);
}

static string SummarizeFailure(string output)
{
    var lines = output
        .Split(new[] { "\r\n", "\n" }, StringSplitOptions.RemoveEmptyEntries)
        .Select(line => line.Trim())
        .Where(line => line.Length > 0)
        .ToArray();

    var selected = new List<string>();
    void Add(string line)
    {
        if (selected.Count >= 12 || selected.Contains(line, StringComparer.Ordinal))
            return;
        selected.Add(line);
    }

    foreach (var line in lines)
        if (line.StartsWith("e:", StringComparison.Ordinal)
            || line.StartsWith("Caused by:", StringComparison.Ordinal)
            || (line.StartsWith("> ", StringComparison.Ordinal)
                && !line.StartsWith("> Task ", StringComparison.Ordinal)
                && !line.StartsWith("> Run with ", StringComparison.Ordinal)
                && !line.StartsWith("> Get more help", StringComparison.Ordinal))
            || line.Contains("missing runtime entries", StringComparison.OrdinalIgnoreCase)
            || line.Contains("Compilation error", StringComparison.OrdinalIgnoreCase)
            || line.Contains("could not find expected anchor", StringComparison.OrdinalIgnoreCase))
            Add(line);

    if (selected.Count == 0)
    {
        foreach (var line in lines.Reverse())
        {
            if (line.StartsWith("at ", StringComparison.Ordinal)
                || line.StartsWith("* ", StringComparison.Ordinal)
                || line.StartsWith("BUILD FAILED", StringComparison.Ordinal))
                continue;
            Add(line);
            if (selected.Count >= 6)
                break;
        }
        selected.Reverse();
    }

    return string.Join(Environment.NewLine, selected);
}

static void Require(bool condition, string message)
{
    if (!condition) throw new Exception(message);
}

Require(ReachedLoginState("LOGIN_SCREEN"), "LOGIN_SCREEN must pass.");
Require(ReachedLoginState("LOGGED_IN"), "LOGGED_IN must pass.");
Require(!ReachedLoginState("STARTING"), "STARTING must not pass.");
Require(!ReachedLoginState("UNKNOWN"), "UNKNOWN must not pass.");

var health = ParseHealth("""{"clientConnected":true,"clientHeartbeatSequence":7,"clientGameState":"LOGIN_SCREEN"}""");
Require(health.Sequence == 7, "Heartbeat sequence parse failed.");
Require(health.State == "LOGIN_SCREEN", "Game state parse failed.");

var summary = SummarizeFailure("""
> Task :server-plugin:verifyOpenRunePluginJar FAILED
* What went wrong:
> Nero OpenRune plugin jar is incomplete; missing runtime entries: StudioProtocol.class
at org.gradle.SomeStack.foo(SomeStack.java:1)
Caused by: java.lang.IllegalArgumentException: missing runtime entries: StudioProtocol.class
> Run with --stacktrace option to get the stack trace.
BUILD FAILED
""");
Require(summary.Contains("missing runtime entries", StringComparison.OrdinalIgnoreCase), "Root cause missing.");
Require(!summary.Contains("> Task", StringComparison.Ordinal), "Task chatter leaked.");
Require(!summary.Contains("SomeStack.java", StringComparison.Ordinal), "Stack trace leaked.");

Console.WriteLine("NERO_LAUNCHER_CONTRACT_PROBE_OK");
