package com.lin.linagent.agent.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseAgentTest {

    @Test
    void emitProgressShouldPreserveWhitespaceOnlyChunk() {
        MockAgent agent = new MockAgent();
        List<AgentProgressEvent> events = new ArrayList<>();
        agent.setCurrentStep(1);
        agent.setProgressConsumer(events::add);

        agent.publish("final", "\n");

        assertEquals(1, events.size());
        assertEquals("final", events.get(0).getType());
        assertEquals("\n", events.get(0).getContent());
    }

    @Test
    void emitProgressShouldIgnoreEmptyChunk() {
        MockAgent agent = new MockAgent();
        List<AgentProgressEvent> events = new ArrayList<>();
        agent.setCurrentStep(1);
        agent.setProgressConsumer(events::add);

        agent.publish("final", "");

        assertEquals(0, events.size());
    }

    private static class MockAgent extends BaseAgent {

        @Override
        public String step() {
            return "";
        }

        void publish(String type, String content) {
            emitProgress(type, content);
        }
    }
}
