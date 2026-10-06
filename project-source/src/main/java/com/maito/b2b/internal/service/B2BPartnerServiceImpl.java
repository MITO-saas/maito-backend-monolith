package com.maito.b2b.internal.service;

import com.maito.b2b.api.dto.B2BPartnerResponse;
import com.maito.b2b.api.dto.RegisterPartnerCommand;
import com.maito.b2b.api.dto.VerifyPartnerCommand;
import com.maito.b2b.api.service.B2BPartnerService;
import com.maito.b2b.internal.domain.B2BCreditLedger;
import com.maito.b2b.internal.domain.B2BPartner;
import com.maito.b2b.internal.repository.B2BCreditLedgerRepository;
import com.maito.b2b.internal.repository.B2BPartnerRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2BPartnerServiceImpl implements B2BPartnerService {

    private final B2BPartnerRepository partnerRepository;
    private final B2BCreditLedgerRepository creditLedgerRepository;

    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    @Override
    @Transactional
    public B2BPartnerResponse registerPartner(UUID customerProfileId, RegisterPartnerCommand cmd) {
        if (customerProfileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Customer profile ID cannot be null");
        }

        if (partnerRepository.existsByCustomerProfileId(customerProfileId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "B2B partner profile already registered for this customer");
        }

        String rawGstin = cmd.gstin() != null ? cmd.gstin().trim().toUpperCase() : "";
        if (!GSTIN_PATTERN.matcher(rawGstin).matches()) {
            throw new BusinessException(ErrorCode.INVALID_GSTIN, "Invalid GSTIN format: " + rawGstin);
        }

        if (partnerRepository.existsByGstin(rawGstin)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "GSTIN already registered: " + rawGstin);
        }

        String pan = (cmd.pan() != null && !cmd.pan().isBlank())
                ? cmd.pan().trim().toUpperCase()
                : rawGstin.substring(2, 12).toUpperCase();

        B2BPartner partner = B2BPartner.builder()
                .customerProfileId(customerProfileId)
                .companyLegalName(cmd.companyLegalName().trim())
                .tradeName(cmd.tradeName() != null ? cmd.tradeName().trim() : null)
                .gstin(rawGstin)
                .pan(pan)
                .fssaiLicenseNumber(cmd.fssaiLicenseNumber() != null ? cmd.fssaiLicenseNumber().trim() : null)
                .verificationStatus("PENDING_VERIFICATION")
                .creditLimit(BigDecimal.ZERO)
                .usedCredit(BigDecimal.ZERO)
                .paymentTermsDays(0)
                .billingAddress(cmd.billingAddress() != null ? cmd.billingAddress() : Map.of())
                .build();

        B2BPartner saved = partnerRepository.save(partner);
        log.info("Registered B2B partner [{}] for customer [{}] with GSTIN [{}]",
                saved.getId(), customerProfileId, saved.getGstin());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public B2BPartnerResponse verifyPartner(UUID partnerId, VerifyPartnerCommand cmd) {
        B2BPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_PARTNER_NOT_FOUND, "B2B partner not found: " + partnerId));

        String newStatus = cmd.verificationStatus() != null ? cmd.verificationStatus().trim().toUpperCase() : "VERIFIED";
        partner.setVerificationStatus(newStatus);

        if (cmd.creditLimit() != null && cmd.creditLimit().compareTo(partner.getCreditLimit()) != 0) {
            BigDecimal oldLimit = partner.getCreditLimit();
            BigDecimal newLimit = cmd.creditLimit();
            partner.setCreditLimit(newLimit);

            String entryType = newLimit.compareTo(oldLimit) > 0 ? "LIMIT_INCREASE" : "LIMIT_DECREASE";
            BigDecimal diff = newLimit.subtract(oldLimit).abs();

            B2BCreditLedger ledgerEntry = B2BCreditLedger.builder()
                    .partnerId(partner.getId())
                    .entryType(entryType)
                    .amount(diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ONE)
                    .usedCreditAfter(partner.getUsedCredit())
                    .referenceId("LIMIT_ADJUSTMENT_" + System.currentTimeMillis())
                    .notes("Admin limit adjustment from " + oldLimit + " to " + newLimit + ". " + (cmd.notes() != null ? cmd.notes() : ""))
                    .build();
            creditLedgerRepository.save(ledgerEntry);
        }

        if (cmd.paymentTermsDays() != null) {
            partner.setPaymentTermsDays(cmd.paymentTermsDays());
        }

        B2BPartner saved = partnerRepository.save(partner);
        log.info("Updated verification status for B2B partner [{}] to [{}], credit limit: [{}], terms: [{} days]",
                saved.getId(), saved.getVerificationStatus(), saved.getCreditLimit(), saved.getPaymentTermsDays());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public B2BPartnerResponse getPartnerByCustomer(UUID customerProfileId) {
        return partnerRepository.findByCustomerProfileId(customerProfileId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_PARTNER_NOT_FOUND, "No B2B partner profile found for customer: " + customerProfileId));
    }

    @Override
    @Transactional(readOnly = true)
    public B2BPartnerResponse getPartnerById(UUID partnerId) {
        return partnerRepository.findById(partnerId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.B2B_PARTNER_NOT_FOUND, "B2B partner not found: " + partnerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<B2BPartnerResponse> listPartners(String verificationStatus, Pageable pageable) {
        if (verificationStatus != null && !verificationStatus.isBlank()) {
            return partnerRepository.findByVerificationStatus(verificationStatus.trim().toUpperCase(), pageable)
                    .map(this::toResponse);
        }
        return partnerRepository.findAll(pageable).map(this::toResponse);
    }

    private B2BPartnerResponse toResponse(B2BPartner p) {
        return new B2BPartnerResponse(
                p.getId(),
                p.getCustomerProfileId(),
                p.getCompanyLegalName(),
                p.getTradeName(),
                p.getGstin(),
                p.getPan(),
                p.getFssaiLicenseNumber(),
                p.getVerificationStatus(),
                p.getCreditLimit(),
                p.getUsedCredit(),
                p.getAvailableCredit(),
                p.getPaymentTermsDays(),
                p.getBillingAddress(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
