package com.linj6.interviewagent.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 完整 Profile replacement 请求。TEXT 字段还校验 UTF-8 字节数。 */
public record InterviewProfileRequest(
        @NotBlank @Size(max = 100) String candidateName,
        @NotNull @Size(max = 65535) String resumeSummary,
        @NotNull @Size(max = 5) List<@NotNull @Valid ProjectRequest> projects,
        @NotBlank @Size(max = 200) String positionTitle,
        @NotNull @Size(max = 65535) String positionDescription) {
    public record ProjectRequest(
            @JsonFormat(shape = JsonFormat.Shape.STRING) @JsonDeserialize(using = DecimalIdDeserializer.class) Long id,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Size(max = 65535) String description) {}
}
