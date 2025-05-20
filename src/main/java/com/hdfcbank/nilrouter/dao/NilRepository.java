package com.hdfcbank.nilrouter.dao;

import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@EnableCaching
public class NilRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    public String findTargetByTxnId(String txnId) {
        String sql = "SELECT target FROM network_il.transaction_audit WHERE txn_id = :txnId";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("txnId", txnId);

        try {
            return namedParameterJdbcTemplate.queryForObject(sql, params, String.class);
        } catch (Exception e) {
            // log and return null or handle accordingly
            return null;
        }

    }


    public void saveDataInMsgEventTracker(MsgEventTracker msgEventTracker) {
        String sql = "INSERT INTO network_il.msg_event_tracker (msg_id, source, target, flow_type, msg_type, original_req, original_req_count, consolidate_amt, intermediate_req, intemdiate_count, status, created_time, modified_timestamp) " +
                "VALUES (:msg_id, :source, :target, :flow_type, :msg_type, (XMLPARSE(CONTENT :original_req)), :original_req_count, :consolidate_amt, XMLPARSE(CONTENT :intermediate_req), :intemdiate_count, :status, :created_time, :modified_timestamp )";

        LocalDateTime timestamp = LocalDateTime.now();


        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("msg_id", msgEventTracker.getMsgId());
        params.addValue("source", msgEventTracker.getSource());
        params.addValue("target", msgEventTracker.getTarget());
        params.addValue("flow_type", msgEventTracker.getFlowType());
        params.addValue("msg_type", msgEventTracker.getMsgType());
        params.addValue("original_req", msgEventTracker.getOrgnlReq());
        params.addValue("original_req_count", msgEventTracker.getOrgnlReqCount());
        params.addValue("consolidate_amt", null);
        params.addValue("intermediate_req", msgEventTracker.getIntermediateReq());
        params.addValue("intemdiate_count", msgEventTracker.getIntermediateCount());
        params.addValue("status", null);
        params.addValue("created_time", timestamp);
        params.addValue("modified_timestamp", null);
        namedParameterJdbcTemplate.update(sql, params);


    }

    public void saveAllTransactionAudits(List<TransactionAudit> transactionAudits) {
        String sql = "INSERT INTO network_il.transaction_audit (" +
                "msg_id, txn_id, end_to_end_id, return_id, req_payload, source, target, " +
                "flow_type, msg_type, amount, batch_id, created_time, modified_timestamp) " +
                "VALUES (:msg_id, :txn_id, :end_to_end_id, :return_id, XMLPARSE(CONTENT :req_payload), " +
                ":source, :target, :flow_type, :msg_type, :amount, :batch_id, :created_time, :modified_timestamp)";

        LocalDateTime timestamp = LocalDateTime.now();

        List<MapSqlParameterSource> batchParams = transactionAudits.stream()
                .map(tx -> {
                    MapSqlParameterSource params = new MapSqlParameterSource();
                    params.addValue("msg_id", tx.getMsgId());
                    params.addValue("txn_id", tx.getTxnId());
                    params.addValue("end_to_end_id", tx.getEndToEndId());
                    params.addValue("return_id", tx.getReturnId());
                    params.addValue("req_payload", tx.getReqPayload());
                    params.addValue("source", tx.getSource());
                    params.addValue("target", tx.getTarget());
                    params.addValue("flow_type", tx.getFlowType());
                    params.addValue("msg_type", tx.getMsgType());
                    params.addValue("amount", tx.getAmount());
                    params.addValue("batch_id", tx.getBatchId());
                    params.addValue("created_time", timestamp);
                    params.addValue("modified_timestamp", null);
                    return params;
                })
                .toList();

        namedParameterJdbcTemplate.batchUpdate(sql, batchParams.toArray(new MapSqlParameterSource[0]));
    }

    public boolean cugAccountExists(String accountNo) {
        String sql = "SELECT COUNT(*) FROM network_il.cug_account WHERE cug_account_no = :accountNo";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountNo", accountNo);

        Integer count = namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);

        return count != null && count > 0;
    }

}
