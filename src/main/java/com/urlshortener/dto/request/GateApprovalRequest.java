package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateApprovalRequest {

    @NotBlank(message = "Approver name is required")
    private String approvedBy;
}
