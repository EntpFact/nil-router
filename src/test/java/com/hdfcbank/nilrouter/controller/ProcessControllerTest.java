package com.hdfcbank.nilrouter.controller;

import com.hdfcbank.nilrouter.model.Response;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProcessControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @InjectMocks
    ProcessController controller;

    @Spy
    private MessageChannel routingChannel = Mockito.mock(MessageChannel.class);


    @Test
    public void testHealthzEndpoint() {
        webTestClient.get().uri("/healthz").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Success");
    }

    @Test
    public void testReadyEndpoint() {
        webTestClient.get().uri("/ready").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Success");
    }


    @Test
    void testProcessEndpoint() {
        String request = "<test>valid xml</test>";

        Mockito.when(routingChannel.send(any())).thenReturn(true);

        webTestClient.post().uri("/testProcess").bodyValue(request).exchange().expectStatus().is5xxServerError().expectBody().jsonPath("$.status").isEqualTo("ERROR");
    }

    @Test
    void testProcess_invalidBase64_shouldReturnError() {
        // Invalid base64 will cause decoding to fail
        String invalidBase64 = "!@#$%^&*()_+";

        webTestClient.post().uri("/process").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("data_base64", invalidBase64)).exchange().expectStatus().is5xxServerError().expectBody(Response.class).value(response -> {
            assert response.getStatus().equals("ERROR");
            assert response.getMessage().equals("Message Processing Failed");
        });

        verifyNoInteractions(routingChannel);
    }

    @Test
    void testValidateXml_usingReflection() throws Exception {
        ProcessController controller = new ProcessController();

        Map<String, Object> cloudEvent = new HashMap<>();
        String xml = "<note><to>Tove</to></note>";
        cloudEvent.put("data_base64", Base64.getEncoder().encodeToString(xml.getBytes()));

        Method method = ProcessController.class.getDeclaredMethod("validateXml", Map.class);
        method.setAccessible(true);

        String result = (String) method.invoke(controller, cloudEvent);

        assertTrue(result.contains("<note>"));
    }

   /* @Test
    void testValidateXml_withBOM_removesBOM() {
        String xmlWithBom = "\uFEFF<test>BOM</test>";
        String base64Encoded = Base64.getEncoder().encodeToString(xmlWithBom.getBytes());

        Map<String, Object> cloudEvent = new HashMap<>();
        cloudEvent.put("data_base64", base64Encoded);
        Method method = ProcessController.class.getDeclaredMethod("removeBOM", Map.class);
        method.setAccessible(true);
//        String result = XmlUtil.validateXml(cloudEvent);

        assertTrue(result.contains("<test>BOM</test>"));
    }*/
//
////    @Test
//    public void testHealthzEndpoint() {
//        webTestClient.get()
//                .uri("/healthz")
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(String.class).isEqualTo("Success");
//    }
//
//    @Test
//    public void testReadyEndpoint() {
//        webTestClient.get()
//                .uri("/ready")
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(String.class).isEqualTo("Success");
//    }
//
//    @Test
//    public void testProcessSuccess() {
//        String xml = "<RequestPayload><AppHdr><BizMsgIdr>TestId</BizMsgIdr></AppHdr></RequestPayload>";
//        String base64Xml = Base64.getEncoder().encodeToString(xml.getBytes());
//        Map<String, Object> request = new HashMap<>();
//        request.put("data_base64", base64Xml);
//
//        webTestClient.post()
//                .uri("/process")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(request)
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(Response.class)
//                .value(response -> {
//                    assert response.getStatus().equals("SUCCESS");
//                    assert response.getMessage().equals("Message Processed.");
//                });
//    }
//
//    @Test
//    public void testTestProcessSuccess() {
//        String xml = "<RequestPayload><AppHdr><BizMsgIdr>TestId</BizMsgIdr></AppHdr></RequestPayload>";
//
//        webTestClient.post()
//                .uri("/testProcess")
//                .contentType(MediaType.TEXT_PLAIN)
//                .bodyValue(xml)
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(Response.class)
//                .value(response -> {
//                    assert response.getStatus().equals("SUCCESS");
//                    assert response.getMessage().equals("Message Processed.");
//                });
//    }
//
//    @Test
//    public void testOutProcessSuccess() {
//        String xml = "<RequestPayload><AppHdr><BizMsgIdr>TestId</BizMsgIdr></AppHdr></RequestPayload>";
//
//        webTestClient.post()
//                .uri("/outProcess")
//                .contentType(MediaType.TEXT_PLAIN)
//                .bodyValue(xml)
//                .exchange()
//                .expectStatus().isOk()
//                .expectBody(Response.class)
//                .value(response -> {
//                    assert response.getStatus().equals("SUCCESS");
//                    assert response.getMessage().equals("Message Processed.");
//                });
//    }
}
