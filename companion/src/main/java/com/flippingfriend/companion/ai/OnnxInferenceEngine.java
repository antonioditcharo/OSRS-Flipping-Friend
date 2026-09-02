package com.flippingfriend.companion.ai;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OnnxInferenceEngine implements AutoCloseable
{
	private static final Logger log = LoggerFactory.getLogger(OnnxInferenceEngine.class);

	private OrtEnvironment env;
	private OrtSession momentumSession;
	private OrtSession fillProbSession;
	private OrtSession waitTimeSession;

	public OnnxInferenceEngine()
	{
		try
		{
			this.env = OrtEnvironment.getEnvironment();
			this.momentumSession = loadModel("models/momentum_v1.onnx");
			this.fillProbSession = loadModel("models/fill_prob_v1.onnx");
			this.waitTimeSession = loadModel("models/queue_wait_v1.onnx");
		}
		catch (Exception e)
		{
			log.error("Failed to initialize ONNX runtime or models", e);
		}
	}

	private OrtSession loadModel(String resourcePath) throws Exception
	{
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath))
		{
			if (is == null)
			{
				log.warn("Model resource not found: " + resourcePath);
				return null;
			}
			byte[] modelBytes = is.readAllBytes();
			OrtSession.SessionOptions options = new OrtSession.SessionOptions();
			options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
			return env.createSession(modelBytes, options);
		}
	}

	public float[][] predictMomentums(float[][][] features)
	{
		if (momentumSession == null || env == null) return new float[features.length][2];
		
		try (OnnxTensor tensor = OnnxTensor.createTensor(env, features);
			 OrtSession.Result result = momentumSession.run(Collections.singletonMap("input", tensor)))
		{
			return (float[][]) result.get(0).getValue();
		}
		catch (OrtException e)
		{
			log.error("Momentum prediction failed", e);
			return new float[features.length][2];
		}
	}

	public float[] predictFillProbabilities(float[][] features)
	{
		if (fillProbSession == null || env == null) return new float[features.length];
		
		try (OnnxTensor tensor = OnnxTensor.createTensor(env, features);
			 OrtSession.Result result = fillProbSession.run(Collections.singletonMap("input", tensor)))
		{
			// LightGBM classifier ONNX models often output labels and probabilities.
			// Depending on onnxmltools, the probabilities might be the second output.
			// We'll return 0s for now if it's tricky, but usually the first output is a long array of labels
			// and the second is a list of maps of probabilities. Let's assume a simplified single output for now.
			
			// For a standard regression export (if we exported as float), we'd just get float[].
			// If it's a list of maps, we need to extract the positive class probability.
			Object val = result.get("probabilities").get().getValue();
			float[] probs = new float[features.length];
			if (val instanceof java.util.List)
			{
				java.util.List<?> list = (java.util.List<?>) val;
				for (int i = 0; i < list.size(); i++)
				{
					Object item = list.get(i);
					if (item instanceof Map)
					{
						probs[i] = ((Map<Long, Float>) item).getOrDefault(1L, 0f);
					}
					else if (item instanceof ai.onnxruntime.OnnxMap)
					{
						probs[i] = (Float) ((Map) ((ai.onnxruntime.OnnxMap) item).getValue()).getOrDefault(1L, 0f);
					}
				}
			}
			return probs;
		}
		catch (Exception e)
		{
			log.error("Fill probability prediction failed", e);
			return new float[features.length];
		}
	}

	public float[] predictWaitTimes(float[][] features)
	{
		if (waitTimeSession == null || env == null) return new float[features.length];
		
		try (OnnxTensor tensor = OnnxTensor.createTensor(env, features);
			 OrtSession.Result result = waitTimeSession.run(Collections.singletonMap("input", tensor)))
		{
			float[][] res = (float[][]) result.get(0).getValue();
			float[] out = new float[features.length];
			for (int i = 0; i < res.length; i++) {
				out[i] = res[i][0];
			}
			return out;
		}
		catch (Exception e)
		{
			log.error("Wait time prediction failed", e);
			return new float[features.length];
		}
	}

	@Override
	public void close()
	{
		try
		{
			if (momentumSession != null) momentumSession.close();
			if (fillProbSession != null) fillProbSession.close();
			if (waitTimeSession != null) waitTimeSession.close();
			if (env != null) env.close();
		}
		catch (OrtException e)
		{
			log.error("Error closing ONNX sessions", e);
		}
	}
}
