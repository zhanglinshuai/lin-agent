package com.lin.linagent.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import com.lin.linagent.contant.CommonVariables;

import java.util.HashMap;
import java.util.Map;

/**
 * Http方式调用agent
 *
 * @author zhanglinshuai
 */
public class HttpInvokeAgent {
    public static void main(String[] args) {
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        //设置请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer" + CommonVariables.API_KEY);
        headers.put("Content-Type", "application/json");
        //设置请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", CommonVariables.MODEL_NAME);

        JSONObject input = new JSONObject();
        JSONObject[] messages = new JSONObject[2];
        //设置系统提示词
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a helpful assistant");
        messages[0] = systemMessage;
        //设置用户提示词
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", "你是谁？");
        messages[1] = userMessage;
        input.put("messages", messages);
        requestBody.put("input", input);
        //设置结果输出格式
        JSONObject parameters = new JSONObject();
        parameters.put("result_format", "message");
        requestBody.put("parameters", parameters);

        //发送请求
        HttpResponse response = HttpRequest.post(url)
                .addHeaders(headers)
                .body(requestBody.toString())
                .execute();
        //处理响应
        if(response.isOk()){
            System.out.println("HTTP请求成功，响应内容:");
        }else {
            System.out.println("HTTP请求失败，状态码："+response.getStatus());
        }
    }
}
