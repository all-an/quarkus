/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.protean.arc.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.DotName;

/**
 *
 * @author Martin Kouba
 */
public class AnnotationStore {

    private final ConcurrentMap<AnnotationTarget, Collection<AnnotationInstance>> transformed;

    private final EnumMap<Kind, List<AnnotationsTransformer>> transformersMap;

    public AnnotationStore(Collection<AnnotationsTransformer> transformers) {
        if (transformers == null || transformers.isEmpty()) {
            this.transformed = null;
            this.transformersMap = null;
        } else {
            this.transformed = new ConcurrentHashMap<>();
            this.transformersMap = new EnumMap<>(Kind.class);
            this.transformersMap.put(Kind.CLASS, initTransformers(Kind.CLASS, transformers));
            this.transformersMap.put(Kind.METHOD, initTransformers(Kind.METHOD, transformers));
            this.transformersMap.put(Kind.FIELD, initTransformers(Kind.FIELD, transformers));
        }
    }

    Collection<AnnotationInstance> getAnnotations(AnnotationTarget target) {
        if (transformed != null) {
            return transformed.computeIfAbsent(target, this::transform);
        }
        return getOriginalAnnotations(target);
    }

    AnnotationInstance getAnnotation(AnnotationTarget target, DotName name) {
        return Annotations.find(getAnnotations(target), name);
    }

    boolean hasAnnotation(AnnotationTarget target, DotName name) {
        return Annotations.contains(getAnnotations(target), name);
    }

    boolean hasAnyAnnotation(AnnotationTarget target, Iterable<DotName> names) {
        return Annotations.containsAny(getAnnotations(target), names);
    }

    private Collection<AnnotationInstance> transform(AnnotationTarget target) {
        Collection<AnnotationInstance> annotations = getOriginalAnnotations(target);
        Kind kind = target.kind();
        List<AnnotationsTransformer> transformers = transformersMap.get(kind);
        if (transformers.isEmpty()) {
            return annotations;
        }
        for (AnnotationsTransformer transformer : transformers) {
            annotations = transformer.transform(target, annotations);
        }
        return annotations;
    }

    private Collection<AnnotationInstance> getOriginalAnnotations(AnnotationTarget target) {
        switch (target.kind()) {
            case CLASS:
                return target.asClass().classAnnotations();
            case METHOD:
                // TODO filter method params?
                return target.asMethod().annotations();
            case FIELD:
                return target.asField().annotations();
            default:
                throw new IllegalArgumentException("Unsupported annotation target");
        }
    }

    private List<AnnotationsTransformer> initTransformers(Kind kind, Collection<AnnotationsTransformer> transformers) {
        List<AnnotationsTransformer> found = new ArrayList<>();
        for (AnnotationsTransformer transformer : transformers) {
            if (transformer.appliesTo(kind)) {
                found.add(transformer);
            }
        }
        if (found.isEmpty()) {
            return Collections.emptyList();
        }
        found.sort(BuildExtension::compare);
        return found;
    }

}
