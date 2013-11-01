package samples;

import hex.*;
import hex.Layer.Tanh;
import hex.Layer.TanhPrime;
import hex.Layer.VecSoftmax;
import hex.Layer.VecsInput;
import water.fvec.Vec;

/**
 * Same as previous MNIST sample but with more layers and pre-training.
 */
public class NeuralNetMnistDeep extends NeuralNetMnist {
  public static void main(String[] args) throws Exception {
    // CloudLocal.launch(1, NeuralNetMnistDeep.class);
    // CloudProcess.launch(4, NeuralNetMnistDeep.class);
    // CloudConnect.launch("localhost:54321", NeuralNetMnistDeep.class);
    CloudRemote.launchIPs(NeuralNetMnistDeep.class);
  }

  @Override public Layer[] build(Vec[] data, Vec labels, VecsInput inputStats, VecSoftmax outputStats) {
    Layer[] ls = new Layer[5];
    ls[0] = new VecsInput(data, inputStats);
    for( int i = 1; i < ls.length - 1; i++ ) {
      ls[i] = new Tanh(500);
      ls[i].rate = .05f;
    }
    ls[ls.length - 1] = new VecSoftmax(labels, outputStats);
    ls[ls.length - 1].rate = .02f;
    for( int i = 0; i < ls.length; i++ ) {
      ls[i].l2 = .0001f;
      ls[i].rateAnnealing = 1 / 1e5f;
      ls[i].init(ls, i);
    }
    return ls;
  }

  @Override Trainer startTraining(Layer[] ls) {
    for( int i = 1; i < ls.length - 1; i++ ) {
      System.out.println("Pre-training level " + i);
      long time = System.nanoTime();
      preTrain(ls, i);
      System.out.println((int) ((System.nanoTime() - time) / 1e6) + " ms");
    }

    Trainer trainer = new Trainer.MapReduce(ls, 0, self());
    //Trainer trainer = new Trainer.Direct(ls);
    trainer.start();
    return trainer;
  }

  void preTrain(Layer[] ls, int index) {
    // Build a network with same layers below 'index', and an auto-encoder at the top
    Layer[] pre = new Layer[index + 2];
    VecsInput input = (VecsInput) ls[0];
    pre[0] = new VecsInput(input.vecs, input);
    for( int i = 1; i < index; i++ ) {
      pre[i] = new Tanh(ls[i].units);
      pre[i].rate = 0;
      Layer.shareWeights(ls[i], pre[i]);
    }
    // Auto-encoder is a tanh and a reverse tanh on top
    pre[index] = new Tanh(ls[index].units);
    pre[index].rate = .01f;
    pre[index + 1] = new TanhPrime(ls[index].units);
    pre[index + 1].rate = .01f;
    Layer.shareWeights(ls[index], pre[index]);
    Layer.shareWeights(ls[index], pre[index + 1]);

    for( int i = 0; i < pre.length; i++ )
      pre[i].init(pre, i, false, 0);

    Trainer.Direct trainer = new Trainer.Direct(pre);
    trainer.samples = 1000;
    trainer.run();
  }
}