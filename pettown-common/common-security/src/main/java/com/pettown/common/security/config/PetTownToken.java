package com.pettown.common.security.config;

import cn.dev33.satoken.stp.StpLogic;

/** Shared user and admin token namespaces; account validation belongs to business modules. */
public final class PetTownToken {
    public static final StpLogic USER = new StpLogic("user");
    public static final StpLogic ADMIN = new StpLogic("admin");

    private PetTownToken() {
    }
}
