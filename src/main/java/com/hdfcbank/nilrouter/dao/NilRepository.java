package com.hdfcbank.nilrouter.dao;

import com.hdfcbank.nilrouter.model.MsgEventTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.hdfcbank.nilrouter.utils.Constants.RECEIVED;

@Slf4j
@Repository
@EnableCaching
public class NilRepository {

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;


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
        params.addValue("source", "");
        params.addValue("target", "");
        params.addValue("flow_type", null);
        params.addValue("msg_type", null);
        params.addValue("original_req", msgEventTracker.getOrgnlReq());
        params.addValue("original_req_count", null);
        params.addValue("consolidate_amt", null);
        params.addValue("intermediate_req", null);
        params.addValue("intemdiate_count", null);
        params.addValue("status", RECEIVED);
        params.addValue("created_time", timestamp);
        params.addValue("modified_timestamp", null);
        namedParameterJdbcTemplate.update(sql, params);

    }

    public List<String> getAllCugAccountNumbers() {
        String sql = "SELECT cug_account_no FROM network_il.cug_account";
        return jdbcTemplate.queryForList(sql, String.class);
    }

}
