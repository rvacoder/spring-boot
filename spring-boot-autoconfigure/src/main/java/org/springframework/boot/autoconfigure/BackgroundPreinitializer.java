/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure;

import javax.validation.Validation;

import org.apache.catalina.mbeans.MBeanFactory;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;

/**
 * {@link ApplicationListener} to trigger early initialization in a background thread of
 * time consuming tasks.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.3.0
 */
@Order(LoggingApplicationListener.DEFAULT_ORDER + 1)
public class BackgroundPreinitializer implements ApplicationListener<ApplicationEvent> {

	private volatile Thread initializationThread;

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationStartedEvent) {
			performInitialization();
		}
		else if (event instanceof ContextRefreshedEvent) {
			awaitInitialization();
		}
	}

	private void performInitialization() {
		try {
			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					runSafely(new MessageConverterInitializer());
					runSafely(new MBeanFactoryInitializer());
					runSafely(new ValidationInitializer());
				}

				public void runSafely(Runnable runnable) {
					try {
						runnable.run();
					}
					catch (Throwable ex) {
						// Ignore
					}
				}

			}, "background-preinit");
			thread.start();
			this.initializationThread = thread;
		}
		catch (Exception ex) {
			// This will fail on GAE where creating threads is prohibited. We can safely
			// continue but startup will be slightly slower as the initialization will now
			// happen on the main thread.
		}
	}

	private void awaitInitialization() {
		Thread thread = this.initializationThread;
		if (thread != null) {
			try {
				thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			finally {
				this.initializationThread = null;
			}
		}
	}

	/**
	 * Early initializer for Spring MessageConverters.
	 */
	private static class MessageConverterInitializer implements Runnable {

		@Override
		public void run() {
			new AllEncompassingFormHttpMessageConverter();
		}

	}

	/**
	 * Early initializer to load Tomcat MBean XML.
	 */
	private static class MBeanFactoryInitializer implements Runnable {

		@Override
		public void run() {
			new MBeanFactory();
		}

	}

	/**
	 * Early initializer for javax.validation.
	 */
	private static class ValidationInitializer implements Runnable {

		@Override
		public void run() {
			Validation.byDefaultProvider().configure();
		}

	}
}
