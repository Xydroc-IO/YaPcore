package com.yapcore.link.plugin;

import com.yapcore.link.api.event.LinkEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple synchronous event bus for YaP Link plugins. */
public final class LinkEventBus {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Events");

    private record Handler(Object listener, Method method) {
        void invoke(LinkEvent event) throws ReflectiveOperationException {
            method.invoke(listener, event);
        }
    }

    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<>();

    public void register(Object listener) {
        List<Handler> found = new ArrayList<>();
        for (Method m : listener.getClass().getMethods()) {
            if (m.getAnnotation(com.yapcore.link.api.annotation.Subscribe.class) == null) {
                continue;
            }
            if (m.getParameterCount() != 1 || !LinkEvent.class.isAssignableFrom(m.getParameterTypes()[0])) {
                LOG.warning("Skipping invalid @Subscribe on " + listener.getClass().getName() + "#" + m.getName());
                continue;
            }
            m.setAccessible(true);
            found.add(new Handler(listener, m));
        }
        handlers.addAll(found);
    }

    public void unregister(Object listener) {
        handlers.removeIf(h -> h.listener() == listener);
    }

    public void fire(LinkEvent event) {
        for (Handler h : handlers) {
            if (!h.method().getParameterTypes()[0].isInstance(event)) {
                continue;
            }
            try {
                h.invoke(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Event handler failed on " + event.getClass().getSimpleName(), e);
            }
        }
    }
}
