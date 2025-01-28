/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class UnsupportedSignatureCypherException extends ChatMessageException {

    private static final long serialVersionUID = -601113302507390165L;

    public UnsupportedSignatureCypherException() {
        super();
    }

    public UnsupportedSignatureCypherException(String msg) {
        super(msg);
    }

    public UnsupportedSignatureCypherException(Throwable er) {
        super(er);
    }

    public UnsupportedSignatureCypherException(String msg, Throwable er) {
        super(msg, er);
    }
}
