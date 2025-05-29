package com.hdfcbank.nilrouter.dao;

import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

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

    @Test
    void testSaveAllTransactionAudits() {
        TransactionAudit tx1 = new TransactionAudit();
        tx1.setMsgId("MSG001");
        tx1.setTxnId("TXN001");
        tx1.setEndToEndId("E2E001");
        tx1.setReturnId("RET001");
        tx1.setReqPayload("<xml>payload</xml>");
        tx1.setSource("SRC");
        tx1.setTarget("TGT");
        tx1.setFlowType("INWARD");
        tx1.setMsgType("pacs.004.001.10");
        tx1.setAmount(BigDecimal.valueOf(100.00));
        tx1.setBatchId("BATCH01");

        TransactionAudit tx2 = new TransactionAudit();
        tx2.setMsgId("MSG002");
        tx2.setTxnId("TXN002");
        tx2.setEndToEndId("E2E002");
        tx2.setReturnId("RET002");
        tx2.setReqPayload("<xml>payload2</xml>");
        tx2.setSource("SRC2");
        tx2.setTarget("TGT2");
        tx2.setFlowType("OUTWARD");
        tx2.setMsgType("pacs.008.001.07");
        tx2.setAmount(BigDecimal.valueOf(200.00));
        tx2.setBatchId("BATCH02");

        List<TransactionAudit> audits = List.of(tx1, tx2);

        nilRepository.saveAllTransactionAudits(audits);

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), any(MapSqlParameterSource[].class));
    }
}

