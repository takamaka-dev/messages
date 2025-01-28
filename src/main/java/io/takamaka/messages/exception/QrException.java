/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class QrException extends MessageException {

    private static final long serialVersionUID = -1515339639921067983L;

    public QrException() {
        super();
    }

    public QrException(String msg) {
        super(msg);
    }

    public QrException(Throwable er) {
        super(er);
    }

    public QrException(String msg, Throwable er) {
        super(msg, er);
    }
}
