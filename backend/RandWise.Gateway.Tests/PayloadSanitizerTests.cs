using RandWise.Gateway;

namespace RandWise.Gateway.Tests;

public class PayloadSanitizerTests
{
    [Theory]
    [InlineData("my account is 62001234567 thanks", "my account is [number] thanks")]
    [InlineData("email me at someone@example.co.za", "email me at [email]")]
    [InlineData("call 082 555 1234", "call [phone]")]
    [InlineData("Can I afford R1500?", "Can I afford R1500?")]
    public void Strips_personal_identifiers_but_keeps_amounts(string input, string expected)
    {
        Assert.Equal(expected, PayloadSanitizer.Clean(input));
    }

    [Fact]
    public void Long_questions_are_truncated()
    {
        Assert.Equal(600, PayloadSanitizer.Clean(new string('a', 5000)).Length);
    }
}
