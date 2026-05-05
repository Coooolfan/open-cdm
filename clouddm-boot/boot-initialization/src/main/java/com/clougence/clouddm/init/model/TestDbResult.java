package com.clougence.clouddm.init.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestDbResult {

    private boolean success;
    private String  message;
    private boolean isEmpty;
    private boolean isInstalled;
}
