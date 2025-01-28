/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class CryptoMessageException extends MessageException {

    private static final long serialVersionUID = 4015124906924691463L;

    public CryptoMessageException() {
        super();
    }

    public CryptoMessageException(String msg) {
        super(msg);
    }

    public CryptoMessageException(Throwable er) {
        super(er);
    }

    public CryptoMessageException(String msg, Throwable er) {
        super(msg, er);
    }
}
