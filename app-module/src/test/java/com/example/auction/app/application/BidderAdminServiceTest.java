package com.example.auction.app.application;

import com.example.auction.app.adapters.out.persistence.BidderJpaEntity;
import com.example.auction.app.adapters.out.persistence.SpringDataBidderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidderAdminServiceTest {

    @Test
    void updateUsesLockedReadAndPersistsChanges() {
        SpringDataBidderRepository repository = mock(SpringDataBidderRepository.class);
        BidderJpaEntity bidder = activeBidder();
        when(repository.findByIdForUpdate("b1")).thenReturn(Optional.of(bidder));
        when(repository.save(bidder)).thenReturn(bidder);

        BidderAdminService service = new BidderAdminService(repository, 2);
        BidderJpaEntity updated = service.update("b1", "Jane", "Doe", "jane@example.com", "NID-2", new BigDecimal("500.00"));

        assertEquals("Jane", updated.getFirstName());
        assertEquals("Doe", updated.getLastName());
        assertEquals("jane@example.com", updated.getEmail());
        assertEquals("NID-2", updated.getNationalId());
        assertEquals(new BigDecimal("500.00"), updated.getPurchasingAuthorizationLimit());
        verify(repository).findByIdForUpdate("b1");
        verify(repository, never()).findById("b1");
        verify(repository).save(bidder);
    }

    @Test
    void blockUsesLockedRead() {
        SpringDataBidderRepository repository = mock(SpringDataBidderRepository.class);
        BidderJpaEntity bidder = activeBidder();
        when(repository.findByIdForUpdate("b1")).thenReturn(Optional.of(bidder));
        when(repository.save(bidder)).thenReturn(bidder);

        BidderAdminService service = new BidderAdminService(repository, 2);
        service.block("b1");

        assertNotNull(bidder.getBlockedUntil());
        verify(repository).findByIdForUpdate("b1");
        verify(repository, never()).findById("b1");
        verify(repository).save(bidder);
    }

    @Test
    void softDeleteUsesLockedReadAndIsIdempotent() {
        SpringDataBidderRepository repository = mock(SpringDataBidderRepository.class);
        BidderJpaEntity bidder = activeBidder();
        when(repository.findByIdForUpdate("b1")).thenReturn(Optional.of(bidder));
        when(repository.save(bidder)).thenReturn(bidder);

        BidderAdminService service = new BidderAdminService(repository, 2);
        service.softDelete("b1");
        OffsetDateTime deletedAt = bidder.getDeletedAt();
        service.softDelete("b1");

        assertNotNull(deletedAt);
        assertEquals(deletedAt, bidder.getDeletedAt());
        verify(repository, times(2)).findByIdForUpdate("b1");
        verify(repository, never()).findById("b1");
        verify(repository).save(bidder);
    }

    private static BidderJpaEntity activeBidder() {
        BidderJpaEntity bidder = new BidderJpaEntity();
        bidder.setId("b1");
        bidder.setFirstName("John");
        bidder.setLastName("Smith");
        bidder.setEmail("john@example.com");
        bidder.setNationalId("NID-1");
        bidder.setPurchasingAuthorizationLimit(new BigDecimal("100.00"));
        bidder.setCreatedAt(OffsetDateTime.now());
        bidder.setDeletedAt(null);
        return bidder;
    }
}
