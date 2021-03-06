package com.example;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SimpleApplication implements Closeable {

	private LauncherCommand launcher;

	@GetMapping
	public Map<String, Object> get() throws Exception {
		return translate(null);
	}

	@RequestMapping
	public Map<String, Object> handle(@RequestBody Map<String, Object> body)
			throws Exception {
		return translate(body);
	}

	public static void main(String[] args) {
		SpringApplication.run(SimpleApplication.class, args);
	}

	private Map<String, Object> translate(Map<String, Object> body) throws Exception {
		if (launcher == null) {
			launcher = new LauncherCommand();
		}
		return launcher.run(body, "classpath:sample.groovy");
	}

	public void close() {
		if (launcher != null) {
			launcher.close();
			launcher = null;
		}
	}

}

class LauncherCommand extends BaseKeyedPooledObjectFactory<String, Object>
		implements Closeable {

	private GenericKeyedObjectPool<String, Object> pool = new GenericKeyedObjectPool<>(
			this);
	private LauncherFactory threadFactory = new LauncherFactory();
	private ExecutorService executor;

	public LauncherCommand() {
		executor = Executors.newCachedThreadPool();
	}

	public synchronized Map<String, Object> run(Map<String, Object> request,
			String source, String... args) throws Exception {
		Callable<Map<String, Object>> callable = () -> {
			Object launcher = pool.borrowObject(source);
			try {
				return exchange(launcher, request);
			}
			finally {
				pool.returnObject(source, launcher);
			}
		};
		Future<Map<String, Object>> task = executor.submit(callable);
		return task.get();
	}

	private Map<String, Object> exchange(Object target, Object... value) {
		Method method = ReflectionUtils.findMethod(target.getClass(), "handle",
				Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) ReflectionUtils
				.invokeMethod(method, target, value);
		return map;
	}

	@Override
	public void destroyObject(String key, PooledObject<Object> p) throws Exception {
		Object target = p.getObject();
		Method method = ReflectionUtils.findMethod(target.getClass(), "shutdown",
				Map.class);
		ReflectionUtils.invokeMethod(method, target);
	}

	@Override
	public Object create(String key) throws Exception {
		// TODO: add args
		return threadFactory.getLauncher(key);
	}

	@Override
	public PooledObject<Object> wrap(Object value) {
		return new DefaultPooledObject<Object>(value);
	}

	public void close() {
		pool.close();
	}

}