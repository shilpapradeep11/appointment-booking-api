package com.dm.appt;

import ai.djl.Model;
import ai.djl.engine.Engine;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import com.dm.appt.repo.AppointmentRepository;
import com.dm.appt.repo.ColleagueRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class AppointmentAllocatorApp {

	static {
		System.setProperty("ai.djl.logging.level", "info");
	}


	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private AppointmentRepository appointmentRepository;

	public static void main(String[] args) {
		System.setProperty("ai.djl.logging.level", "info");
		System.setProperty("ai.onnxruntime.logger", "WARNING");
		Engine engine = Engine.getEngine("OnnxRuntime");
		System.out.println("Loaded engine: " + engine.getEngineName());
		System.out.println("******* Engines List ****** "+Engine.getAllEngines());
		SpringApplication.run(AppointmentAllocatorApp.class, args);
	}

	@Bean
	public Queue queue() {
		return new Queue("appointmentQueue", false);
	}

	@Bean
	public TopicExchange exchange() {
		return new TopicExchange("appointmentExchange");
	}

	@Bean
	public Binding binding(Queue queue, TopicExchange exchange) {
		return BindingBuilder.bind(queue).to(exchange).with("appointment.request");
	}

	/*@PostConstruct
	public void loadData() {
		ColleagueRepository colleagueRepository = applicationContext.getBean(ColleagueRepository.class);
		colleagueRepository.save(new Colleague(null, "Alice", true));
		colleagueRepository.save(new Colleague(null, "Bob", true));
	}*/

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(jsonMessageConverter());
		return template;
	}

	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
			ConnectionFactory connectionFactory,
			MessageConverter messageConverter
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter);
		return factory;
	}

	@PostConstruct
	public void registerOrtLogger() {
		System.setProperty("ai.onnxruntime.default_logger_severity", "warning");
	}


}
