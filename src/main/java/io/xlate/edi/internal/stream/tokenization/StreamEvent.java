package io.xlate.edi.internal.stream.tokenization;

import java.nio.CharBuffer;

import io.xlate.edi.internal.stream.CharArraySequence;
import io.xlate.edi.internal.stream.StaEDIStreamLocation;
import io.xlate.edi.schema.EDIReference;
import io.xlate.edi.schema.implementation.EDITypeImplementation;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamValidationError;
import io.xlate.edi.stream.Location;

public class StreamEvent {

    private static final String TOSTRING_FORMAT = "type: %s, error: %s, data: %s, typeReference: %s, location: { %s }";

    EDIStreamEvent type;
    EDIStreamValidationError errorType;

    CharBuffer data;
    boolean dataNull = true;

    EDIReference typeReference;

    StaEDIStreamLocation location;

    @Override
    public String toString() {
        return String.format(TOSTRING_FORMAT, type, errorType, data, typeReference, location);
    }

    public EDIStreamEvent getType() {
        return type;
    }

    public CharBuffer getData() {
        return dataNull ? null : data;
    }

    public void setData(CharSequence data) {
        if (data instanceof CharArraySequence) {
            this.data = put(this.data, (CharArraySequence) data);
            this.dataNull = false;
        } else if (data != null) {
            this.data = put(this.data, data);
            this.dataNull = false;
        } else {
            this.dataNull = true;
        }
    }

    public String getReferenceCode() {
        if (typeReference instanceof EDITypeImplementation) {
            return ((EDITypeImplementation) typeReference).getCode();
        }

        if (typeReference != null) {
            return typeReference.getReferencedType().getCode();
        }

        return null;
    }

    public EDIReference getTypeReference() {
        return typeReference;
    }

    public void setTypeReference(EDIReference typeReference) {
        this.typeReference = typeReference;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        if (this.location == null) {
            this.location = new StaEDIStreamLocation(location);
        }

        this.location.set(location);
    }

    static CharBuffer put(CharBuffer buffer, CharArraySequence data) {
        final int length = data.length();

        if (buffer == null || buffer.capacity() < length) {
            buffer = CharBuffer.allocate(length);
        }

        buffer.clear();

        if (length > 0) {
            data.putToBuffer(buffer);
        }

        buffer.flip();

        return buffer;
    }

    static CharBuffer put(CharBuffer buffer, CharSequence text) {
        int length = text.length();

        if (buffer == null || buffer.capacity() < length) {
            buffer = CharBuffer.allocate(length);
        }

        buffer.clear();
        for (int i = 0; i < length; i++) {
            buffer.put(text.charAt(i));
        }
        buffer.flip();

        return buffer;
    }
}
