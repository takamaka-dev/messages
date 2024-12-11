/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.exception;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class InvalidParameterException extends MessageException {

    private static final long serialVersionUID = 1L;

    public InvalidParameterException() {
        super();
    }

    public InvalidParameterException(String msg) {
        super(msg);
    }

    public InvalidParameterException(Throwable er) {
        super(er);
    }

    public InvalidParameterException(String msg, Throwable er) {
        super(msg, er);
    }
}
