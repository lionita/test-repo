package com.example.auction.app.adapters.out.persistence;

import com.example.auction.auction.domain.AuctionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpringDataRepositoriesTest {

    @Autowired
    private SpringDataAuctionRepository auctionRepository;

    @Autowired
    private SpringDataBidRepository bidRepository;

    @Autowired
    private SpringDataBidderRepository bidderRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @Test
    void auctionRepositoryFindLiveEndingAtOrBefore_filtersByStatusAndTime() {
        AuctionJpaEntity liveSoon = auction("liveSoon", AuctionStatus.LIVE, OffsetDateTime.now().minusMinutes(1));
        AuctionJpaEntity liveLater = auction("liveLater", AuctionStatus.LIVE, OffsetDateTime.now().plusHours(1));
        AuctionJpaEntity endedSoon = auction("endedSoon", AuctionStatus.CLOSED, OffsetDateTime.now().minusMinutes(2));

        auctionRepository.saveAll(List.of(liveSoon, liveLater, endedSoon));

        List<AuctionJpaEntity> results = auctionRepository.findLiveEndingAtOrBefore(OffsetDateTime.now());

        assertThat(results)
                .extracting(AuctionJpaEntity::getId)
                .containsExactly(liveSoon.getId());
    }

    @Test
    void bidRepositoryMethods_returnExpectedValues() {
        UUID auctionId = UUID.randomUUID();
        BidJpaEntity first = bid(auctionId, 1, new BigDecimal("100.00"), "idempotency-1");
        BidJpaEntity second = bid(auctionId, 2, new BigDecimal("150.00"), "idempotency-2");
        BidJpaEntity third = bid(auctionId, 3, new BigDecimal("150.00"), "idempotency-3");
        bidRepository.saveAll(List.of(first, second, third));

        assertThat(bidRepository.maxSequenceByAuctionId(auctionId)).isEqualTo(3);
        assertThat(bidRepository.existsByBidderIdAndIdempotencyKey("bidder-2", "idempotency-2")).isTrue();
        assertThat(bidRepository.existsByBidderIdAndIdempotencyKey("bidder-2", "missing")).isFalse();

        BidJpaEntity highestBid = bidRepository.findFirstByAuctionIdOrderByAmountDescSequenceNumberAsc(auctionId)
                .orElseThrow();
        assertThat(highestBid.getId()).isEqualTo(second.getId());
    }

    @Test
    void bidderRepositoryFindByIdAndDeletedAtIsNull_excludesSoftDeletedBidder() {
        BidderJpaEntity active = bidder("active-bidder", null);
        BidderJpaEntity deleted = bidder("deleted-bidder", OffsetDateTime.now());
        bidderRepository.saveAll(List.of(active, deleted));

        assertThat(bidderRepository.findByIdAndDeletedAtIsNull("active-bidder")).isPresent();
        assertThat(bidderRepository.findByIdAndDeletedAtIsNull("deleted-bidder")).isEmpty();
    }

    @Test
    void outboxRepositoryClaimReadyToPublish_returnsOnlyPendingReadyEvents() {
        OutboxEventJpaEntity ready = outboxEvent(OffsetDateTime.now().minusMinutes(10), null, null, null);
        OutboxEventJpaEntity futureAttempt = outboxEvent(OffsetDateTime.now().minusMinutes(9), null, OffsetDateTime.now().plusMinutes(5), null);
        OutboxEventJpaEntity alreadyPublished = outboxEvent(OffsetDateTime.now().minusMinutes(8), OffsetDateTime.now(), null, null);
        OutboxEventJpaEntity deadLettered = outboxEvent(OffsetDateTime.now().minusMinutes(7), null, null, OffsetDateTime.now());
        outboxRepository.saveAll(List.of(ready, futureAttempt, alreadyPublished, deadLettered));

        List<OutboxEventJpaEntity> claimed = outboxRepository.claimReadyToPublish(OffsetDateTime.now());

        assertThat(claimed)
                .extracting(OutboxEventJpaEntity::getId)
                .containsExactly(ready.getId());
    }

    private static AuctionJpaEntity auction(String suffix, AuctionStatus status, OffsetDateTime endTime) {
        AuctionJpaEntity entity = new AuctionJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setTitle("Title " + suffix);
        entity.setDescription("Description " + suffix);
        entity.setReservePrice(new BigDecimal("100.00"));
        entity.setMinIncrement(new BigDecimal("10.00"));
        entity.setStartTime(OffsetDateTime.now().minusHours(1));
        entity.setEndTime(endTime);
        entity.setStatus(status);
        entity.setCurrentPrice(new BigDecimal("120.00"));
        return entity;
    }

    private static BidJpaEntity bid(UUID auctionId, long sequence, BigDecimal amount, String idempotencyKey) {
        BidJpaEntity entity = new BidJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAuctionId(auctionId);
        entity.setBidderId("bidder-" + sequence);
        entity.setAmount(amount);
        entity.setSequenceNumber(sequence);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setCreatedAt(OffsetDateTime.now().minusMinutes(5).plusSeconds(sequence));
        return entity;
    }

    private static BidderJpaEntity bidder(String id, OffsetDateTime deletedAt) {
        BidderJpaEntity entity = new BidderJpaEntity();
        entity.setId(id);
        entity.setFirstName("First");
        entity.setLastName("Last");
        entity.setEmail(id + "@example.com");
        entity.setNationalId("NAT-" + id);
        entity.setPurchasingAuthorizationLimit(new BigDecimal("1000.00"));
        entity.setCreatedAt(OffsetDateTime.now().minusDays(1));
        entity.setDeletedAt(deletedAt);
        return entity;
    }

    private static OutboxEventJpaEntity outboxEvent(
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime deadLetteredAt
    ) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType("auction.created");
        entity.setAggregateId(UUID.randomUUID());
        entity.setPayload("{}");
        entity.setCreatedAt(createdAt);
        entity.setPublishedAt(publishedAt);
        entity.setPublishAttempts(0);
        entity.setNextAttemptAt(nextAttemptAt);
        entity.setDeadLetteredAt(deadLetteredAt);
        return entity;
    }
}
