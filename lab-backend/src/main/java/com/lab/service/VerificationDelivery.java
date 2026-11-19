package com.lab.service;
/** Provider boundary, injectable in tests. Codes must never be returned to HTTP or logs. */
public interface VerificationDelivery {
 void send(String channel,String target,String code);
 boolean enabled(String channel);
}
