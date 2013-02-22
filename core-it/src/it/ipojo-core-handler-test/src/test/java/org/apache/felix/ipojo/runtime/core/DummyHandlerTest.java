package org.apache.felix.ipojo.runtime.core;

import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.apache.felix.ipojo.*;
import org.apache.felix.ipojo.runtime.core.components.DummyImpl;
import org.apache.felix.ipojo.runtime.core.handlers.DummyHandler;
import org.apache.felix.ipojo.runtime.core.services.Dummy;
import org.junit.Test;
import org.mockito.Mockito;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.OptionUtils;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.useradmin.User;
import org.ow2.chameleon.testing.tinybundles.ipojo.IPOJOStrategy;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;


@ExamReactorStrategy(PerMethod.class)
public class DummyHandlerTest extends Common {

    private static final String DUMMY_TEST_FACTORY = "dummy.test";

    /*
     * Number of mock object by test.
     */
    private static final int NB_MOCK = 10;


    @Configuration
    public Option[] config() throws IOException {
        Option[] options = super.config();

        // Build handler bundle
        File handlerJar = new File("target/bundles/handler.jar");
        FileUtils.copyInputStreamToFile(
                TinyBundles.bundle()
                        .add(DummyHandler.class)
                        .set(org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME, "Dummy.Handler")
                        .build(IPOJOStrategy.withiPOJO(new File("src/main/resources/dummy-handler.xml"))),
                handlerJar);

        // Build consumer bundle
        File dummyJar = new File("target/bundles/dummy.jar");
        FileUtils.copyInputStreamToFile(
                TinyBundles.bundle()
                        .add(DummyImpl.class)
                        .set(org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME, "Dummy.Bundle")
                        .build(IPOJOStrategy.withiPOJO(new File("src/main/resources/dummy-component.xml"))),
                dummyJar);


        return OptionUtils.combine(options,
                streamBundle(TinyBundles.bundle()
                        .add(Dummy.class)
                        .set(Constants.EXPORT_PACKAGE, "org.apache.felix.ipojo.runtime.core.services")
                        .set(Constants.BUNDLE_SYMBOLICNAME, "service")
                        .build(withBnd())
                ),
                bundle(handlerJar.toURI().toURL().toExternalForm()),
                bundle(dummyJar.toURI().toURL().toExternalForm()),
                mavenBundle().groupId("org.apache.felix").artifactId("org.osgi.compendium").version("1.4.0"));
    }

    /**
     * Basic Test, in order to know if the instance is correctly create.
     */
    @Test
    public void testDummyTestInstance() {
        ComponentInstance instance;

        // Get the factory
        Factory factory = Tools.getValidFactory(osgiHelper, DUMMY_TEST_FACTORY);
        Assert.assertNotNull(factory);

        // Create an instance
        try {
            instance = factory.createComponentInstance(null);
        } catch (UnacceptableConfiguration e) {
            throw new AssertionError(e);
        } catch (MissingHandlerException e) {
            throw new AssertionError(e);
        } catch (ConfigurationException e) {
            throw new AssertionError(e);
        }

        // Must be valid now
        Assert.assertEquals(instance.getState(), ComponentInstance.VALID);

        // Stop the instance
        instance.stop();
        Assert.assertEquals(instance.getState(), ComponentInstance.STOPPED);

        // Start the instance
        instance.start();
        Assert.assertEquals(instance.getState(), ComponentInstance.VALID);
    }

    /**
     * Test if the bind and unbind methods are called when the bind service are registered after the instance creation
     */
    @Test
    public void testDummyTestBindAfterStart() {
        // Get the factory
        Factory factory = Tools.getValidFactory(osgiHelper, DUMMY_TEST_FACTORY);
        assertNotNull(factory);

        // Create an instance
        ComponentInstance instance = ipojoHelper.createComponentInstance(DUMMY_TEST_FACTORY);

        Map<User, ServiceRegistration> registrations = new HashMap<User, ServiceRegistration>();

        for (int i = 0; i < NB_MOCK; i++) {
            User service = mock(User.class);
            ServiceRegistration sr = bc.registerService(User.class.getName(), service, null);
            registrations.put(service, sr);
        }

        //verify that the bind method of the handler has been called
        for (User user : registrations.keySet()) {
            verify(user).getName();
        }

        //verify that the unbind has been called
        for (User user : registrations.keySet()) {
            registrations.get(user).unregister();
            verify(user).getType();
        }

        //verify no more interaction
        for (User user : registrations.keySet()) {
            Mockito.verifyNoMoreInteractions(user);
        }
    }


    /**
     * Test if the bind and unbind methods when the bind services are registered before the instance creation
     */
    @Test
    public void testDummyTestBindBeforeStart() {
        ComponentInstance instance = null;

        Map<User, ServiceRegistration> registrations = new HashMap<User, ServiceRegistration>();

        for (int i = 0; i < NB_MOCK; i++) {
            User service = mock(User.class);
            ServiceRegistration sr = bc.registerService(User.class.getName(), service, null);
            registrations.put(service, sr);
        }

        // Get the factory
        Factory factory = Tools.getValidFactory(osgiHelper, DUMMY_TEST_FACTORY);
        assertNotNull(factory);

        instance = ipojoHelper.createComponentInstance(DUMMY_TEST_FACTORY);

        //verify that the bind method of the handler has been called
        for (User user : registrations.keySet()) {
            verify(user).getName();
        }

        //verify that the unbind has been called
        for (User user : registrations.keySet()) {
            registrations.get(user).unregister();
            verify(user).getType();
        }

        //verify no more interaction
        for (User user : registrations.keySet()) {
            Mockito.verifyNoMoreInteractions(user);
        }
    }
}
