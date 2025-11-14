package com.lin.linagent.agent.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ReAct模式的agent
 * 实现思考-行动的循环
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReActAgent extends BaseAgent{
    /**
     * 处于当前状态并且决定下一步行动
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 决定执行的行动
     * @return
     */
    public abstract String act();


    @Override
    public String step() {
        try{
            boolean shouldAct = think();
            if(!shouldAct){
                return "思考完成 - 无需行动";
            }
            return act();
        }catch (Exception e){
            e.printStackTrace();
            return "步骤执行失败"+e.getMessage();
        }
    }
}
