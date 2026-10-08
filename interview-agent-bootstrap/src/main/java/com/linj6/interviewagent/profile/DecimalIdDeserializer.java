package com.linj6.interviewagent.profile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Web ID 输入只接受规范正整数十进制字符串；null 仍表示新增项目。 */
public final class DecimalIdDeserializer extends StdDeserializer<Long> {
    public DecimalIdDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getText();
            if (value.matches("[1-9][0-9]{0,18}")) {
                try {
                    return Long.valueOf(value);
                } catch (NumberFormatException ignored) {
                    // Out-of-range strings are rejected at the HTTP boundary.
                }
            }
        }
        return (Long)
                context.handleUnexpectedToken(Long.class, parser.currentToken(), parser, "ID 必须是 Long 范围内的正整数十进制字符串");
    }
}
