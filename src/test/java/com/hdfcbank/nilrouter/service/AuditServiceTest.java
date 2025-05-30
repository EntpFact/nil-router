package com.hdfcbank.nilrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.config.MessageXPathConfig;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditServiceTest {

    @Mock
    private NilRepository nilRepository;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    @Mock
    private MessageXPathConfig xPathConfig;

    @InjectMocks
    private AuditService auditService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(auditService, "msgEventTrackerTopic", "test-topic");
        ReflectionTestUtils.setField(auditService, "objectMapper", objectMapper);
    }
    @Test
    public void testAuditData_WithInvalidXML() {
        String invalidXml = "<Invalid><UnclosedTag></Invalid";

        assertDoesNotThrow(() -> auditService.auditData(invalidXml));
        verify(nilRepository, never()).saveAllTransactionAudits(any());
    }
//    @Test
    public void testAuditData_ForPacs004() throws Exception {
        String xml = appHdr + pacs004Xml;

        Map<String, String> xPathMap = new HashMap<>();
        xPathMap.put("pacs.004.001.10", "//*[local-name()='CdtTrfTxInf']");
        when(xPathConfig.getMappings()).thenReturn(xPathMap);

        auditService.auditData(xml);

//        ArgumentCaptor<List<TransactionAudit>> captor = ArgumentCaptor.forClass(List.class);
//        verify(nilRepository).saveAllTransactionAudits(captor.capture());

//        List<TransactionAudit> audits = captor.getValue();
//        assertEquals(1, audits.size());
//        TransactionAudit tx = audits.get(0);
//        assertEquals("MSG123", tx.getMsgId());
//        assertEquals("E2E123", tx.getEndToEndId());
//        assertEquals("TX123", tx.getTxnId());
//        assertEquals(new BigDecimal("1000.00"), tx.getAmount());
//        assertEquals("Batch001", tx.getBatchId());
    }

//    @Test
    public void testConstructOutwardJsonAndPublish() throws Exception {
        String xml = appHdr + pacs004Xml;

        when(utilityMethods.getBizMsgIdr(any())).thenReturn("MSG123");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");
        when(utilityMethods.getTotalAmount(any())).thenReturn(new BigDecimal("1000.00"));

        auditService.constructOutwardJsonAndPublish(xml);

//        verify(kafkaUtils).publishToResponseTopic(contains("MSG123"), eq("test-topic"));
    }



    private final String pacs004Xml = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.004.001.10">
                           <PmtRtr>
                               <GrpHdr>
                                   <MsgId>RBIP202101146147295847</MsgId>
                                   <CreDtTm>2021-01-14T16:01:00</CreDtTm>
                                   <NbOfTxs>10</NbOfTxs>
                                   <TtlRtrdIntrBkSttlmAmt Ccy="INR">1000.00</TtlRtrdIntrBkSttlmAmt>
                                   <IntrBkSttlmDt>2021-04-20</IntrBkSttlmDt>
                                   <SttlmInf>
                                       <SttlmMtd>CLRG</SttlmMtd>
                                   </SttlmInf>
                       			<InstgAgt>
                                       <FinInstnId>
                                           <ClrSysMmbId>
                                               <MmbId>RBIP0NEFTSC</MmbId>
                                           </ClrSysMmbId>
                                       </FinInstnId>
                                   </InstgAgt>
                                   <InstdAgt>
                                       <FinInstnId>
                                           <ClrSysMmbId>
                                               <MmbId>HDFC0000001</MmbId>
                                           </ClrSysMmbId>
                                       </FinInstnId>
                                   </InstdAgt>
                               </GrpHdr>
                               <TxInf>
                                   <RtrId>ICICN52013110104801831</RtrId>
                                   <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>
                       			<OrgnlTxId>HDFCN52022062824954013</OrgnlTxId>
                                   <RtrdIntrBkSttlmAmt Ccy="INR">100.00</RtrdIntrBkSttlmAmt>   \s
                                   <RtrRsnInf>
                                       <Rsn>
                                           <Cd>AC01</Cd>
                                       </Rsn>
                                   </RtrRsnInf>
                                   <OrgnlTxRef>
                                       <UndrlygCstmrCdtTrf>
                                           <Dbtr>
                                               <Nm>praveen</Nm>
                       						<PstlAdr>
                       							<AdrLine>IFTAS,HYDERABAD</AdrLine>
                       							<AdrLine>sssssssssssssssssssssssssssssssssss</AdrLine>
                       							<AdrLine>sssssssssssssssssssssssssssssssssss</AdrLine>
                       						</PstlAdr>
                       						<Id>
                       							<OrgId>
                       								<LEI>HB7FFAZ10OMZ8PP8OE26</LEI>
                       							</OrgId>
                       						</Id>
                                           </Dbtr>
                                           <DbtrAcct>
                                               <Id>
                                                   <Othr>
                       								<Id>12445345</Id>
                                                   </Othr>
                                               </Id>
                       						 <Tp>
                       							<Cd>10</Cd>
                       						</Tp>
                                           </DbtrAcct>
                                           <DbtrAgt>
                                               <FinInstnId>
                                                   <ClrSysMmbId>
                       								<MmbId>HDFC0065012</MmbId>
                                                   </ClrSysMmbId>
                                               </FinInstnId>
                                           </DbtrAgt>
                                           <CdtrAgt>
                                               <FinInstnId>
                                                   <ClrSysMmbId>
                       								<MmbId>ICIC0000002</MmbId>
                                                   </ClrSysMmbId>
                                               </FinInstnId>
                                           </CdtrAgt>
                                           <Cdtr>
                                               <Nm>maniwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww</Nm>
                       						<PstlAdr>
                       							<AdrLine>IFTAS,HYDERABAD</AdrLine>
                       							<AdrLine>sssssssssssssssssssssssssssssssssss</AdrLine>
                       							<AdrLine>sssssssssssssssssssssssssssssssssss</AdrLine>
                       						</PstlAdr>
                       						<Id>
                       							<OrgId>
                       								<LEI>HB7FFAZ10OMZ8PP8OE26</LEI>
                       							</OrgId>
                       						</Id>
            
                                           </Cdtr>
                                           <CdtrAcct>
                                               <Id>
                                                   <Othr>
                       								<Id>333333</Id>
                                                   </Othr>
                                               </Id>
                       						<Tp>
                       							<Cd>10</Cd>
                       						</Tp>
                                           </CdtrAcct>
                                           <InstrForCdtrAgt>
                                               <InstrInf>Credit to tcssssssssssssssssssssss
                       ssssssssssssssssssssssssssssssssss
                       ssssssssssssssssssssssssssssssssss
                       ssssssssssssssssssssssssssssssssss</InstrInf>
                                           </InstrForCdtrAgt>
                       					<RmtInf>
                       						<Ustrd>BatchId:0002</Ustrd>
                       						<Ustrd>sdfffffffffffffffffffffffffffffffff</Ustrd>
                       						<Ustrd>sdfffffffffffffffffffffffffffffffff</Ustrd>
                       						<Ustrd>sdfffffffffffffffffffffffffffffffff</Ustrd>
                       					</RmtInf>
                                       </UndrlygCstmrCdtTrf>
                                   </OrgnlTxRef>
                               </TxInf>
             </PmtRtr>
            </Document>
            </RequestPayload>
            """;

    private final String appHdr = """
            <RequestPayload>
                      <AppHdr xmlns="urn:iso:std:iso:20022:tech:xsd:head.001.001.02" xmlns:sig="http://www.w3.org/2000/09/xmldsig#" xmlns:xsi="urn:iso:std:iso:20022:tech:xsd:Header">
                          <To>
                              <FIId>
                                  <FinInstnId>
                                      <ClrSysMmbId>
                                          <MmbId>RBIP0NEFTSC</MmbId>
                                      </ClrSysMmbId>
                                  </FinInstnId>
                              </FIId>
                          </To>
                          <Fr>
                              <FIId>
                                  <FinInstnId>
                                      <ClrSysMmbId>
                                          <MmbId>HDFC0000001</MmbId>
                                      </ClrSysMmbId>
                                  </FinInstnId>
                              </FIId>
                          </Fr>
                          <BizMsgIdr>RBIP202101146147295848</BizMsgIdr>
                          <MsgDefIdr>pacs.004.001.10</MsgDefIdr>
                          <BizSvc>NEFTPaymentReturn</BizSvc>
                          <CreDt>2021-01-14T16:01:00Z</CreDt>
                      	<Sgntr>
                      		<sig:XMLSgntrs>-----BEGIN PKCS7-----
                      -----END PKCS7-----</sig:XMLSgntrs>
                      	</Sgntr>
                      </AppHdr>
            """;

}

