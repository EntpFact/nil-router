package com.hdfcbank.nilrouter.dao;

import com.hdfcbank.nilrouter.model.MsgEventTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

class NilRepositoryTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private NilRepository nilRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        nilRepository = new NilRepository();
        nilRepository.namedParameterJdbcTemplate = jdbcTemplate; // inject mock manually
    }

    @Test
    void testSaveDataInMsgEventTracker() {
        MsgEventTracker tracker = new MsgEventTracker();
        tracker.setMsgId("MSG001");
        tracker.setSource("SOURCE1");
        tracker.setTarget("TARGET1");
        tracker.setFlowType("INWARD");
        tracker.setMsgType("pacs.004.001.10");
        tracker.setOrgnlReq("<xml>original</xml>");
        tracker.setOrgnlReqCount(1);
        tracker.setConsolidateAmt(BigDecimal.TEN);
        tracker.setIntermediateReq("<xml>intermediate</xml>");
        tracker.setIntermediateCount(1);

        nilRepository.saveDataInMsgEventTracker(tracker);

        verify(jdbcTemplate, times(1)).update(anyString(), any(MapSqlParameterSource.class));
    }

}

