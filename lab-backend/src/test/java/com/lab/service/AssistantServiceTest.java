package com.lab.service;
import com.lab.config.*;import com.lab.common.*;import com.lab.vo.AssistantAnswer;
import com.lab.dto.AssistantTurn;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.*;import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class AssistantServiceTest {
 AssistantProperties properties=new AssistantProperties();RestTemplate http=new AssistantConfig().assistantHttp();RateLimitService limiter=mock(RateLimitService.class);
 AssistantService service=new AssistantService(properties,http,limiter);MockRestServiceServer server=MockRestServiceServer.createServer(http);
 void enable(){properties.setEnabled(true);properties.setApiKey("test-key-not-real");}
 @Test void boundedHistoryPreservesOrderAndNeverPersistsBetweenRequests(){
  enable();AssistantTurn turn=new AssistantTurn();turn.setQuestion("设备开机黑屏");turn.setAnswer("请记录故障现象并报修");
  server.expect(requestTo(properties.getEndpoint()))
   .andExpect(jsonPath("$.messages.length()").value(4))
   .andExpect(jsonPath("$.messages[0].role").value("system"))
   .andExpect(jsonPath("$.messages[1].role").value("user"))
   .andExpect(jsonPath("$.messages[1].content").value("设备开机黑屏"))
   .andExpect(jsonPath("$.messages[2].role").value("assistant"))
   .andExpect(jsonPath("$.messages[2].content").value("请记录故障现象并报修"))
   .andExpect(jsonPath("$.messages[3].content").value("缩短成一句话"))
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"黑屏待检\"}}]}",MediaType.APPLICATION_JSON));
  server.expect(requestTo(properties.getEndpoint())).andExpect(jsonPath("$.messages.length()").value(2))
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"新回答\"}}]}",MediaType.APPLICATION_JSON));
  assertEquals("黑屏待检",service.ask(1L,"缩短成一句话",Collections.singletonList(turn)).getAnswer());
  service.ask(2L,"新会话");server.verify();
 }
 @Test void invalidHistoryIsRejectedBeforeProviderAndRateLimit(){
  enable();AssistantTurn t=new AssistantTurn();t.setQuestion("q");t.setAnswer("a");
  assertThrows(BusinessException.class,()->service.ask(1L,"q",Arrays.asList(t,t,t,t)));
  assertThrows(BusinessException.class,()->service.ask(1L,"q",Collections.singletonList(null)));
  t.setAnswer(" ");assertThrows(BusinessException.class,()->service.ask(1L,"q",Collections.singletonList(t)));
  verifyNoInteractions(limiter);server.verify();
 }
 @Test void disabledDoesNotContactProviderOrPretendAI(){assertFalse(service.available());assertEquals("guide",service.ask(1L,"预约").getMode());verifyNoInteractions(limiter);server.verify();}
 @Test void compatibleRequestHasBoundedOutputAndOnlyTwoMessages(){enable();server.expect(requestTo(properties.getEndpoint())).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer test-key-not-real")).andExpect(jsonPath("$.model").value("qwen-plus")).andExpect(jsonPath("$.max_tokens").value(500)).andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[1].content").value("怎么签到"))
  .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"按时签到\"}}]}",MediaType.APPLICATION_JSON));
  AssistantAnswer answer=service.ask(1L,"怎么签到");assertEquals("model",answer.getMode());assertEquals("按时签到",answer.getAnswer());server.verify();}
 @Test void upstreamErrorsNeverExposeBodyOrKey(){enable();server.expect(requestTo(properties.getEndpoint())).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider secret details"));AssistantAnswer answer=service.ask(1L,"问");assertEquals("unavailable",answer.getMode());assertFalse(answer.getAnswer().contains("secret"));}
 @Test void deepseekDisablesThinkingAndReadsFinalAnswer(){
  enable();properties.setEndpoint("https://api.deepseek.com/chat/completions");properties.setModel("deepseek-flash");
  server.expect(requestTo(properties.getEndpoint())).andExpect(jsonPath("$.model").value("deepseek-flash"))
   .andExpect(jsonPath("$.thinking.type").value("disabled")).andExpect(jsonPath("$.max_tokens").value(500))
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"reasoning_content\":\"internal\",\"content\":\"进入设备页申请预约\"}}]}",MediaType.APPLICATION_JSON));
  AssistantAnswer answer=service.ask(1L,"如何预约");assertEquals("model",answer.getMode());assertEquals("进入设备页申请预约",answer.getAnswer());server.verify();
 }
 @Test void otherProvidersDoNotReceiveDeepseekSpecificFields(){
  enable();server.expect(requestTo(properties.getEndpoint())).andExpect(jsonPath("$.thinking").doesNotExist())
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"操作指南\"}}]}",MediaType.APPLICATION_JSON));
  assertEquals("model",service.ask(1L,"帮助").getMode());server.verify();
 }
 @Test void malformedAndOversizedResponsesFallBackAndReleasePermit(){enable();for(int i=0;i<3;i++)server.expect(requestTo(properties.getEndpoint())).andRespond(withSuccess(i==0?"not json":String.join("",CollectionsForTest.big()),MediaType.APPLICATION_JSON));for(int i=0;i<3;i++)assertEquals("unavailable",service.ask(1L,"问").getMode());server.verify();}
 @Test void nonHttpsConfigurationNeverSendsKey(){enable();properties.setEndpoint("http://localhost/chat");assertEquals("unavailable",service.ask(1L,"问").getMode());server.verify();}
 @Test void limitDenialDoesNotInvokeProvider(){enable();doThrow(new BusinessException(ResultCode.TOO_MANY_REQUESTS)).when(limiter).check("assistant-minute","1",5,60);assertThrows(BusinessException.class,()->service.ask(1L,"问"));server.verify();}
 static class CollectionsForTest {static java.util.List<String> big(){return java.util.Collections.nCopies(70000,"x");}}
}
