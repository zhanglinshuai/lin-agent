package com.lin.linagent.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
@Data
public class DeleteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4381129061062266884L;

    /**
     * id
     */
    private Long id;

}
