/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.utils;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public enum NONCE_STATUS {
    VALID,
    EXPIRED,
    /**
     * already used
     */
    REDEEMED
}
