package com.maito.returns.api.service;

import com.maito.returns.api.dto.CreateReturnCommand;
import com.maito.returns.api.dto.ReturnFilter;
import com.maito.returns.api.dto.ReturnResponse;
import com.maito.returns.api.dto.SubmitQcCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReturnService {
    ReturnResponse createReturnRequest(UUID customerProfileId, CreateReturnCommand cmd);
    ReturnResponse approveReturn(UUID returnId);
    ReturnResponse submitQcEvaluation(UUID returnId, SubmitQcCommand cmd);
    Page<ReturnResponse> getCustomerReturns(UUID customerProfileId, Pageable pageable);
    Page<ReturnResponse> getAllReturns(ReturnFilter filter, Pageable pageable);
    ReturnResponse getReturnById(UUID returnId);
}