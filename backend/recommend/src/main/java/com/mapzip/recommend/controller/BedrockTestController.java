package com.mapzip.recommend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@RestController
@RequiredArgsConstructor
public class BedrockTestController {

    private final BedrockRuntimeClient bedrockRuntimeClient;

    @GetMapping("/bedrock-test")
    public String testBedrock() {
    	String modelId = "anthropic.claude-3-sonnet-20240229-v1:0";

    	String body = """
    	{
    	  "anthropic_version": "bedrock-2023-05-31",
    	  "messages": [
    	    {
    	      "role": "user",
    	      "content": "서울 강남 맛집 3개 추천해줘."
    	    }
    	  ],
    	  "max_tokens": 300,
    	  "temperature": 0.7,
    	  "top_p": 0.9,
    	  "top_k": 250
    	}
    	""";

    	InvokeModelRequest request = InvokeModelRequest.builder()
    	        .modelId(modelId)
    	        .contentType("application/json")
    	        .accept("application/json")
    	        .body(SdkBytes.fromUtf8String(body))
    	        .build();

    	InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
    	return response.body().asUtf8String();

    }
}
