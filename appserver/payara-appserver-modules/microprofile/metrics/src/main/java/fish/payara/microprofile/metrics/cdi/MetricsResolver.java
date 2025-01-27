/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *    Copyright (c) [2018-2019] Payara Foundation and/or its affiliates. All rights reserved.
 * 
 *     The contents of this file are subject to the terms of either the GNU
 *     General Public License Version 2 only ("GPL") or the Common Development
 *     and Distribution License("CDDL") (collectively, the "License").  You
 *     may not use this file except in compliance with the License.  You can
 *     obtain a copy of the License at
 *     https://github.com/payara/Payara/blob/master/LICENSE.txt
 *     See the License for the specific
 *     language governing permissions and limitations under the License.
 * 
 *     When distributing the software, include this License Header Notice in each
 *     file and include the License file at glassfish/legal/LICENSE.txt.
 * 
 *     GPL Classpath Exception:
 *     The Payara Foundation designates this particular file as subject to the "Classpath"
 *     exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *     file that accompanied this code.
 * 
 *     Modifications:
 *     If applicable, add the following below the License Header, with the fields
 *     enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyright [year] [name of copyright owner]"
 * 
 *     Contributor(s):
 *     If you wish your version of this file to be governed by only the CDDL or
 *     only the GPL Version 2, indicate your decision by adding "[Contributor]
 *     elects to include this software in this distribution under the [CDDL or GPL
 *     Version 2] license."  If you don't indicate a single choice of license, a
 *     recipient has the option to distribute your version of this file under
 *     either the CDDL, the GPL Version 2 or to extend the choice of license to
 *     its licensees as provided above.  However, if you add GPL Version 2 code
 *     and therefore, elected the GPL Version 2 license, then the option applies
 *     only if the new code is made subject to such option by the copyright
 *     holder.
 *
 * *****************************************************************************
 * Copyright (c) 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
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

package fish.payara.microprofile.metrics.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Vetoed;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import static org.eclipse.microprofile.metrics.MetricType.COUNTER;
import static org.eclipse.microprofile.metrics.MetricType.GAUGE;
import static org.eclipse.microprofile.metrics.MetricType.METERED;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

@ApplicationScoped
public class MetricsResolver {

    public <E extends Member & AnnotatedElement> Of<Counted> counted(Class<?> bean, E element) {
        return resolverOf(bean, element, Counted.class);
    }
    
     public <E extends Member & AnnotatedElement> Of<ConcurrentGauge> concurrentGauge(Class<?> bean, E element) {
        return resolverOf(bean, element, ConcurrentGauge.class);
    }
    
    public Of<ConcurrentGauge> concurrentGauge(Class<?> bean, Method method) {
        return resolverOf(bean, method, ConcurrentGauge.class);
    }

    public Of<Gauge> gauge(Class<?> bean, Method method) {
        return resolverOf(bean, method, Gauge.class);
    }

    public <E extends Member & AnnotatedElement> Of<Metered> metered(Class<?> bean, E element) {
        return resolverOf(bean, element, Metered.class);
    }

    public <E extends Member & AnnotatedElement> Of<Timed> timed(Class<?> bean, E element) {
        return resolverOf(bean, element, Timed.class);
    }

    private <E extends Member & AnnotatedElement, T extends Annotation> Of<T> resolverOf(Class<?> bean, E element, Class<T> metric) {
        if (element.isAnnotationPresent(metric)) {
            return elementResolverOf(element, metric);
        } else {
            return beanResolverOf(bean, element, metric);
        }
    }

    private <E extends Member & AnnotatedElement, T extends Annotation> Of<T> elementResolverOf(E element, Class<T> metric) {
        T annotation = element.getAnnotation(metric);
        String name = metricName(element, annotation);
        String[] tags = tags(annotation);
        return new ValidMetric<>(annotation, name, getMetadata(name, annotation), MetricsHelper.tagsFromString(tags));
    }

    private <E extends Member & AnnotatedElement, T extends Annotation> Of<T> beanResolverOf(Class<?> bean, E element, Class<T> metric) {
        if (bean.isAnnotationPresent(metric)) {
            T annotation = bean.getAnnotation(metric);
            String name = metricName(bean, element, annotation);
            String[] tags = tags(annotation);
            
            return new ValidMetric<>(annotation, name, getMetadata(name, annotation), MetricsHelper.tagsFromString(tags));
        } else if (bean.getSuperclass() != null) {
            return beanResolverOf(bean.getSuperclass(), element, metric);
        }
        return new InvalidMetric<>();
    }

    private <E extends Member & AnnotatedElement> String metricName(E element, Annotation annotation) {
        String name = metricName(annotation);
        String metric = name.isEmpty() ? defaultName(element) : name;
        return isMetricAbsolute(annotation) ? metric
                : MetricRegistry.name(element.getDeclaringClass(), metric);
    }

    private <E extends Member & AnnotatedElement> String metricName(Class<?> bean, E element, Annotation annotation) {
        String name = metricName(annotation);
        String metric = name.isEmpty() ? bean.getSimpleName() : name;
        return isMetricAbsolute(annotation) ? MetricRegistry.name(metric, defaultName(element))
                : MetricRegistry.name(bean.getPackage().getName(), metric, defaultName(element));
    }

    private <E extends Member & AnnotatedElement> String defaultName(E element) {
        if (element instanceof Constructor) {
            return element.getDeclaringClass().getSimpleName();
        } else {
            return element.getName();
        }
    }

    private String metricName(Annotation annotation) {
        if (Counted.class.isInstance(annotation)) {
            return ((Counted) annotation).name();
        } else if (ConcurrentGauge.class.isInstance(annotation)) {
            return ((ConcurrentGauge) annotation).name();
        } else if (Gauge.class.isInstance(annotation)) {
            return ((Gauge) annotation).name();
        } else if (Metered.class.isInstance(annotation)) {
            return ((Metered) annotation).name();
        } else if (Timed.class.isInstance(annotation)) {
            return ((Timed) annotation).name();
        } else {
            throw new IllegalArgumentException("Unsupported Metrics [" + annotation.getClass().getName() + "]");
        }
    }
    
    private String[] tags(Annotation annotation) {
        if (Counted.class.isInstance(annotation)) {
            return ((Counted) annotation).tags();
        } else if (ConcurrentGauge.class.isInstance(annotation)) {
            return ((ConcurrentGauge) annotation).tags();
        } else if (Gauge.class.isInstance(annotation)) {
            return ((Gauge) annotation).tags();
        } else if (Metered.class.isInstance(annotation)) {
            return ((Metered) annotation).tags();
        } else if (Timed.class.isInstance(annotation)) {
            return ((Timed) annotation).tags();
        } else {
            throw new IllegalArgumentException("Unsupported Metrics [" + annotation.getClass().getName() + "]");
        }
    }

    private boolean isMetricAbsolute(Annotation annotation) {
        if (Counted.class.isInstance(annotation)) {
            return ((Counted) annotation).absolute();
        } else if (ConcurrentGauge.class.isInstance(annotation)) {
            return ((ConcurrentGauge) annotation).absolute();
        } else if (Gauge.class.isInstance(annotation)) {
            return ((Gauge) annotation).absolute();
        } else if (Metered.class.isInstance(annotation)) {
            return ((Metered) annotation).absolute();
        } else if (Timed.class.isInstance(annotation)) {
            return ((Timed) annotation).absolute();
        } else {
            throw new IllegalArgumentException("Unsupported Metrics [" + annotation.getClass().getName() + "]");
        }
    }

  public <T extends Annotation> Metadata getMetadata(String name, T annotation) {
        MetadataBuilder metadataBuilder = Metadata.builder();
        metadataBuilder = metadataBuilder.withName(name);
        String[] tags;
        if (Counted.class.isInstance(annotation)) {
            Counted counted = (Counted) annotation;
            metadataBuilder = metadataBuilder.withDisplayName(counted.displayName())
                .withDescription(counted.description())
                .withType(COUNTER)
                .withUnit(counted.unit());
            if (counted.reusable()) {
                metadataBuilder = metadataBuilder.reusable();
            } else {
                metadataBuilder = metadataBuilder.notReusable();
            }
            tags = counted.tags();
        } else if (ConcurrentGauge.class.isInstance(annotation)) {
            ConcurrentGauge gauge = (ConcurrentGauge) annotation;
            metadataBuilder = metadataBuilder.withDisplayName(gauge.displayName())
                .withDescription(gauge.description())
                .withType(MetricType.CONCURRENT_GAUGE)
                .withUnit(gauge.unit());
            if (gauge.reusable()) {
                metadataBuilder = metadataBuilder.reusable();
            } else {
                metadataBuilder = metadataBuilder.notReusable();
            }
            tags = gauge.tags();
        } else if (Gauge.class.isInstance(annotation)) {
            Gauge gauge = (Gauge) annotation;
            metadataBuilder = metadataBuilder.withDisplayName(gauge.displayName())
                .withDescription(gauge.description())
                .withType(GAUGE)
                .withUnit(gauge.unit())
                .notReusable();
            tags = gauge.tags();
        } else if (Metered.class.isInstance(annotation)) {
            Metered metered = (Metered) annotation;
            metadataBuilder = metadataBuilder.withDisplayName(metered.displayName())
                .withDescription(metered.description())
                .withType(METERED)
                .withUnit(metered.unit());
            if (metered.reusable()) {
                metadataBuilder = metadataBuilder.reusable();
            } else {
                metadataBuilder = metadataBuilder.notReusable();
            }
            tags = metered.tags();
        } else if (Timed.class.isInstance(annotation)) {
            Timed timed = (Timed) annotation;
            metadataBuilder.withDisplayName(timed.displayName())
                .withDescription(timed.description())
                .withType(TIMER)
                .withUnit(timed.unit());
            if (timed.reusable()) {
                metadataBuilder = metadataBuilder.reusable();
            } else {
                metadataBuilder = metadataBuilder.notReusable();
            }
            tags = timed.tags();
        } else {
            throw new IllegalArgumentException("Unsupported Metrics [" + annotation.getClass().getName() + "]");
        }
        return metadataBuilder.build();
    }

    @Vetoed
    private static final class ValidMetric<T extends Annotation> implements Of<T> {

        private final T annotation;

        private final String name;

        private final Metadata metadata;
        
        private final Tag[] tags;

        private ValidMetric(T annotation, String name, Metadata metadata, Tag[] tags) {
            this.annotation = annotation;
            this.name = name;
            this.metadata = metadata;
            this.tags = tags;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String metricName() {
            return name;
        }

        @Override
        public T metricAnnotation() {
            return annotation;
        }

        @Override
        public Metadata metadata() {
            return metadata;
        }
        
        @Override 
        public Tag[] tags() {
            return tags;
        }

        @Override
        public MetricID metricID() {
            return new MetricID(name, tags);
        }
        
        
    }

    @Vetoed
    private static final class InvalidMetric<T extends Annotation> implements Of<T> {

        private InvalidMetric() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public String metricName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T metricAnnotation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Metadata metadata() {
            return null;
        }
        
        @Override
        public Tag[] tags() {
            return new Tag[0];
        }

        @Override
        public MetricID metricID() {
            throw new UnsupportedOperationException();
        }

    }

    public interface Of<T extends Annotation> {

        boolean isPresent();

        String metricName();

        T metricAnnotation();

        Metadata metadata();
        
        Tag[] tags();
        
        MetricID metricID();
    }

}
