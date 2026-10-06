package com.maito.b2b.api.service;

import com.maito.b2b.api.dto.B2BPartnerResponse;
import com.maito.b2b.api.dto.RegisterPartnerCommand;
import com.maito.b2b.api.dto.VerifyPartnerCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface B2BPartnerService {
    B2BPartnerResponse registerPartner(UUID customerProfileId, RegisterPartnerCommand cmd);
    B2BPartnerResponse verifyPartner(UUID partnerId, VerifyPartnerCommand cmd);
    B2BPartnerResponse getPartnerByCustomer(UUID customerProfileId);
    B2BPartnerResponse getPartnerById(UUID partnerId);
    Page<B2BPartnerResponse> listPartners(String verificationStatus, Pageable pageable);
}
