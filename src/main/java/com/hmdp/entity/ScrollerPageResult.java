package com.hmdp.entity;

import lombok.Data;

import java.util.List;

@Data
public class ScrollerPageResult {

    private long offset;
    private List<?> list;
    private long lastId;

}
