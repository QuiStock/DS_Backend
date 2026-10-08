package com.quistock.ds_backend.service;

record TeamMemberChanges(
    String name,
    String email,
    String status,
    Long storeId,
    Long regionId,
    boolean assignmentRequested) {}
