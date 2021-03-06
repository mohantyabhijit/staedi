/*******************************************************************************
 * Copyright 2017 xlate.io LLC, http://www.xlate.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package io.xlate.edi.internal.stream.tokenization;

import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.xlate.edi.internal.stream.CharArraySequence;
import io.xlate.edi.internal.stream.StaEDIStreamLocation;
import io.xlate.edi.internal.stream.validation.UsageError;
import io.xlate.edi.internal.stream.validation.Validator;
import io.xlate.edi.schema.EDIReference;
import io.xlate.edi.schema.EDIType;
import io.xlate.edi.schema.Schema;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamValidationError;
import io.xlate.edi.stream.Location;

public class ProxyEventHandler implements EventHandler {

    private final StaEDIStreamLocation location;

    private Schema controlSchema;
    private Validator controlValidator;

    private Schema transactionSchema;
    private Validator transactionValidator;

    private boolean transactionSchemaAllowed = false;
    private boolean transaction = false;

    private InputStream binary;
    private String segmentTag;
    private CharArraySequence elementHolder = new CharArraySequence();

    private StreamEvent[] events = new StreamEvent[99];
    private int eventCount = 0;
    private int eventIndex = 0;
    private Dialect dialect;

    public ProxyEventHandler(StaEDIStreamLocation location, Schema controlSchema) {
        this.location = location;
        setControlSchema(controlSchema, true);
        for (int i = 0; i < 99; i++) {
            events[i] = new StreamEvent();
        }
    }

    public void setControlSchema(Schema controlSchema, boolean validateCodeValues) {
        if (controlValidator != null) {
            throw new IllegalStateException("control validator already created");
        }

        this.controlSchema = controlSchema;
        controlValidator = controlSchema != null ? new Validator(controlSchema, validateCodeValues, null) : null;
    }

    public boolean isTransactionSchemaAllowed() {
        return transactionSchemaAllowed;
    }

    public Schema getTransactionSchema() {
        return this.transactionSchema;
    }

    public void setTransactionSchema(Schema transactionSchema) {
        if (!Objects.equals(this.transactionSchema, transactionSchema)) {
            this.transactionSchema = transactionSchema;
            transactionValidator = transactionSchema != null ? new Validator(transactionSchema, true, controlSchema) : null;
        }
    }

    public void resetEvents() {
        eventCount = 0;
        eventIndex = 0;
    }

    public EDIStreamEvent getEvent() {
        if (hasEvents()) {
            return events[eventIndex].type;
        }
        return null;
    }

    public CharBuffer getCharacters() {
        if (hasEvents()) {
            return events[eventIndex].getData();
        }
        throw new IllegalStateException();
    }

    public boolean hasEvents() {
        return eventIndex < eventCount;
    }

    public boolean nextEvent() {
        if (eventCount < 1) {
            return false;
        }
        return ++eventIndex < eventCount;
    }

    public EDIStreamValidationError getErrorType() {
        return events[eventIndex].errorType;
    }

    public String getReferenceCode() {
        return hasEvents() ? events[eventIndex].getReferenceCode() : null;
    }

    public Location getLocation() {
        if (hasEvents() && events[eventIndex].location != null) {
            return events[eventIndex].location;
        }
        return location;
    }

    public InputStream getBinary() {
        return binary;
    }

    public void setBinary(InputStream binary) {
        this.binary = binary;
    }

    public EDIReference getSchemaTypeReference() {
        return hasEvents() ? events[eventIndex].getTypeReference() : null;
    }

    @Override
    public void interchangeBegin(Dialect dialect) {
        this.dialect = dialect;
        enqueueEvent(EDIStreamEvent.START_INTERCHANGE, EDIStreamValidationError.NONE, "", null, location);
    }

    @Override
    public void interchangeEnd() {
        Validator validator = validator();

        if (validator != null) {
            validator.validateLoopSyntax(this);
        }

        enqueueEvent(EDIStreamEvent.END_INTERCHANGE, EDIStreamValidationError.NONE, "", null, location);
    }

    @Override
    public void loopBegin(EDIReference typeReference) {
        final String loopCode = typeReference.getReferencedType().getCode();

        if (EDIType.Type.TRANSACTION.toString().equals(loopCode)) {
            transaction = true;
            transactionSchemaAllowed = true;
            enqueueEvent(EDIStreamEvent.START_TRANSACTION, EDIStreamValidationError.NONE, loopCode, typeReference, location);
            if (transactionValidator != null) {
                transactionValidator.reset();
            }
        } else if (EDIType.Type.GROUP.toString().equals(loopCode)) {
            enqueueEvent(EDIStreamEvent.START_GROUP, EDIStreamValidationError.NONE, loopCode, typeReference, location);
        } else {
            enqueueEvent(EDIStreamEvent.START_LOOP, EDIStreamValidationError.NONE, loopCode, typeReference, location);
        }
    }

    @Override
    public void loopEnd(EDIReference typeReference) {
        final String loopCode = typeReference.getReferencedType().getCode();

        // Validator can not be null when a loopEnd event has been signaled.
        validator().validateLoopSyntax(this);

        if (EDIType.Type.TRANSACTION.toString().equals(loopCode)) {
            transaction = false;
            dialect.transactionEnd();
            enqueueEvent(EDIStreamEvent.END_TRANSACTION, EDIStreamValidationError.NONE, loopCode, typeReference, location);
        } else if (EDIType.Type.GROUP.toString().equals(loopCode)) {
            dialect.groupEnd();
            enqueueEvent(EDIStreamEvent.END_GROUP, EDIStreamValidationError.NONE, loopCode, typeReference, location);
        } else {
            enqueueEvent(EDIStreamEvent.END_LOOP, EDIStreamValidationError.NONE, loopCode, typeReference, location);
        }
    }

    @Override
    public boolean segmentBegin(String segmentTag) {
        this.segmentTag = segmentTag;

        /*
         * If this is the start of a transaction, loopStart will be called from the validator and
         * transactionSchemaAllowed will be `true` for the duration of the start-transaction segment.
         */
        transactionSchemaAllowed = false;
        Validator validator = validator();
        boolean eventsReady = true;
        EDIReference typeReference = null;

        if (validator != null && !dialect.isServiceAdviceSegment(segmentTag)) {
            validator.validateSegment(this, segmentTag);
            typeReference = validator.getSegmentReferenceCode();
            eventsReady = !validator.isPendingDiscrimination();
        }

        if (exitTransaction(segmentTag)) {
            // Validate the syntax for the elements directly within the transaction loop
            if (validator != null) {
                validator.validateLoopSyntax(this);
            }

            transaction = false;

            // Now the control validator after setting transaction to false
            validator = validator();
            validator.validateSegment(this, segmentTag);
            typeReference = validator().getSegmentReferenceCode();
        }

        enqueueEvent(EDIStreamEvent.START_SEGMENT, EDIStreamValidationError.NONE, segmentTag, typeReference, location);
        return eventsReady;
    }

    boolean exitTransaction(CharSequence tag) {
        return transaction && !transactionSchemaAllowed && controlSchema != null
                && controlSchema.containsSegment(tag.toString());
    }

    @Override
    public boolean segmentEnd() {
        if (validator() != null) {
            validator().validateSyntax(dialect, this, this, location, false);
            validator().validateVersionConstraints(dialect, this);
        }

        location.clearSegmentLocations();
        enqueueEvent(EDIStreamEvent.END_SEGMENT, EDIStreamValidationError.NONE, segmentTag, null, location);
        return true;
    }

    @Override
    public boolean compositeBegin(boolean isNil) {
        EDIReference typeReference = null;
        boolean eventsReady = true;

        if (validator() != null && !isNil) {
            boolean invalid = !validator().validCompositeOccurrences(dialect, location);

            if (invalid) {
                typeReference = validator().getElementReference();
                List<UsageError> errors = validator().getElementErrors();

                for (UsageError error : errors) {
                    enqueueEvent(error.getError().getCategory(), error.getError(), "", error.getTypeReference(), location);
                }
            } else {
                typeReference = validator().getCompositeReference();
            }
            eventsReady = !validator().isPendingDiscrimination();
        }

        enqueueEvent(EDIStreamEvent.START_COMPOSITE, EDIStreamValidationError.NONE, "", typeReference, location);
        return eventsReady;
    }

    @Override
    public boolean compositeEnd(boolean isNil) {
        boolean eventsReady = true;

        if (validator() != null && !isNil) {
            validator().validateSyntax(dialect, this, this, location, true);
            eventsReady = !validator().isPendingDiscrimination();
        }

        location.clearComponentPosition();
        enqueueEvent(EDIStreamEvent.END_COMPOSITE, EDIStreamValidationError.NONE, "", null, location);
        return eventsReady;
    }

    @Override
    public boolean elementData(char[] text, int start, int length) {
        boolean derivedComposite;
        EDIReference typeReference;
        boolean eventsReady = true;

        elementHolder.set(text, start, length);
        dialect.elementData(elementHolder, location);
        Validator validator = validator();

        if (validator != null) {
            derivedComposite = validateElement(validator);
            typeReference = validator.getElementReference();
        } else {
            derivedComposite = false;
            typeReference = null;
        }

        if (text != null && (!derivedComposite || length > 0) /* Not an inferred element */) {
            enqueueEvent(EDIStreamEvent.ELEMENT_DATA,
                         EDIStreamValidationError.NONE,
                         elementHolder,
                         typeReference,
                         location);

            if (validator != null && validator.isPendingDiscrimination()) {
                eventsReady = validator.selectImplementation(events, eventIndex, eventCount, this);
            }
        }

        if (derivedComposite && text != null /* Not an empty composite */) {
            this.compositeEnd(length == 0);
            location.clearComponentPosition();
        }

        return eventsReady;
    }

    boolean validateElement(Validator validator) {
        final boolean composite = location.getComponentPosition() > -1;
        boolean valid = validator.validateElement(dialect, location, elementHolder);
        boolean derivedComposite = !composite && validator.isComposite();

        if (!valid) {
            /*
             * Process element-level errors before possibly starting a
             * composite or reporting other data-related errors.
             */
            List<UsageError> errors = validator.getElementErrors();
            Iterator<UsageError> cursor = errors.iterator();

            while (cursor.hasNext()) {
                UsageError error = cursor.next();

                switch (error.getError()) {
                case TOO_MANY_DATA_ELEMENTS:
                case TOO_MANY_REPETITIONS:
                    enqueueEvent(error.getError().getCategory(),
                                 error.getError(),
                                 elementHolder,
                                 error.getTypeReference(),
                                 location);
                    cursor.remove();
                    //$FALL-THROUGH$
                default:
                    continue;
                }
            }
        }

        if (derivedComposite && elementHolder.getText() != null/* Not an empty composite */) {
            this.compositeBegin(elementHolder.length() == 0);
            location.incrementComponentPosition();
        }

        if (!valid) {
            List<UsageError> errors = validator.getElementErrors();

            for (UsageError error : errors) {
                enqueueEvent(error.getError().getCategory(),
                             error.getError(),
                             elementHolder,
                             error.getTypeReference(),
                             location);
            }
        }

        return derivedComposite;
    }

    public boolean isBinaryElementLength() {
        return validator() != null && validator().isBinaryElementLength();
    }

    @Override
    public boolean binaryData(InputStream binaryStream) {
        enqueueEvent(EDIStreamEvent.ELEMENT_DATA_BINARY, EDIStreamValidationError.NONE, "", null, location);
        setBinary(binaryStream);
        return true;
    }

    @Override
    public void segmentError(CharSequence token, EDIReference typeReference, EDIStreamValidationError error) {
        enqueueEvent(EDIStreamEvent.SEGMENT_ERROR, error, token, typeReference, location);
    }

    @Override
    public void elementError(final EDIStreamEvent event,
                             final EDIStreamValidationError error,
                             final EDIReference typeReference,
                             final CharSequence data,
                             final int element,
                             final int component,
                             final int repetition) {

        StaEDIStreamLocation copy = location.copy();
        copy.setElementPosition(element);
        copy.setElementOccurrence(repetition);
        copy.setComponentPosition(component);

        enqueueEvent(event, error, data, typeReference, copy);
    }

    private Validator validator() {
        // Do not use the transactionValidator in the period where it may be set/mutated by the user
        return transaction && !transactionSchemaAllowed ? transactionValidator : controlValidator;
    }

    private void enqueueEvent(EDIStreamEvent event,
                              EDIStreamValidationError error,
                              CharSequence data,
                              EDIReference typeReference,
                              Location location) {

        final int index = eventCount;
        StreamEvent target = events[index];
        EDIStreamEvent associatedEvent = (index > 0) ? getAssociatedEvent(error) : null;

        if (eventExists(associatedEvent, index)) {
            /*
             * Ensure segment errors occur before other event types
             * when the array has other events already present.
             */
            int offset = index;
            boolean complete = false;

            while (!complete) {
                if (events[offset - 1].type == associatedEvent) {
                    complete = true;
                } else {
                    events[offset] = events[offset - 1];
                    offset--;
                }
            }

            events[offset] = target;
        }

        target.type = event;
        target.errorType = error;
        target.setData(data);
        target.setTypeReference(typeReference);
        target.setLocation(location);

        eventCount++;
    }

    private boolean eventExists(EDIStreamEvent associatedEvent, int index) {
        int offset = index;

        while (associatedEvent != null && offset > 0) {
            if (events[offset - 1].type == associatedEvent) {
                return true;
            }
            offset--;
        }

        return false;
    }

    private static EDIStreamEvent getAssociatedEvent(EDIStreamValidationError error) {
        final EDIStreamEvent event;

        switch (error) {
        case IMPLEMENTATION_LOOP_OCCURS_UNDER_MINIMUM_TIMES:
            event = EDIStreamEvent.END_LOOP;
            break;
        case MANDATORY_SEGMENT_MISSING:
        case IMPLEMENTATION_SEGMENT_BELOW_MINIMUM_USE:
            event = null;
            break;
        default:
            event = null;
            break;
        }

        return event;
    }
}
