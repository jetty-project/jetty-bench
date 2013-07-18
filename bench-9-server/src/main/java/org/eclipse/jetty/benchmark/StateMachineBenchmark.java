package org.eclipse.jetty.benchmark;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class StateMachineBenchmark
{
    static Random random = new Random();
    
    final BenchmarkHelper helper = new BenchmarkHelper();
    
    public static void main(String[] args) throws Exception
    {
        StateMachineBenchmark bm = new StateMachineBenchmark();

        int size=10000000;
        char[] chars = new char[size];
        for (int i=0;i<size;i++)
            chars[i]="SPH-".charAt(random.nextInt(4));

        Machine switching = new SwitchingMachine();
        Machine polymorphic = new PolymorphicMachine();
        
        bm.test(4, switching,chars);
        bm.test(4, polymorphic,chars);
        bm.test(4, switching,chars);
        bm.test(4, polymorphic,chars);
        bm.test(4, switching,chars);
        bm.test(4, polymorphic,chars);
        bm.test(4, switching,chars);
        bm.test(4, polymorphic,chars);
    }


    private void test(int threads,final Machine machine,final char[] chars) throws Exception
    {
        // System.err.println(new String(chars));

        final CountDownLatch latch = new CountDownLatch(threads);

        helper.startStatistics();

        for (int i=0;i<threads;i++)
        {
            new Thread()
            {
                @Override
                public void run()
                {
                    for (char c: chars)
                    {
                        switch(c)
                        {
                            case 'S':
                                machine.playSheldon();
                                break;
                            case 'P':
                                machine.seePenny();
                                break;
                            case 'H':
                                machine.thinkOfHoward();
                                break;
                            case '-':
                                Thread.yield();
                                break;
                        }
                    }
                    latch.countDown();
                }
            }.start();
        }
        latch.await();
        System.err.println(machine.getClass().getSimpleName()+" "+machine);
        helper.stopStatistics();
    }

    interface Machine
    {
        public void playSheldon();
        public void seePenny();
        public void thinkOfHoward();

    }

    static class SwitchingMachine implements Machine
    {
        enum State {ROCK,PAPER,SCISSORS,LIZARD,SPOCK};
        
        final AtomicReference<State> _state= new AtomicReference<>(State.SPOCK);

        @Override
        public void playSheldon()
        {
            loop: while(true)
            {
                State state=_state.get();
                switch(state)
                {
                    case LIZARD:
                        if (!_state.compareAndSet(state,State.PAPER))
                            continue;
                        break loop;
                    case PAPER:
                        if (!_state.compareAndSet(state,State.LIZARD))
                            continue;
                        break loop;
                    default:
                        if (!_state.compareAndSet(state,State.LIZARD))
                            continue;
                        break loop;
                }
            }
        }

        @Override
        public void seePenny()
        {
            loop: while(true)
            {
                State state=_state.get();
                switch(state)
                {
                    case SPOCK:
                        if (!_state.compareAndSet(state,State.ROCK))
                            continue;
                        break loop;
                    case LIZARD:
                        if (!_state.compareAndSet(state,State.PAPER))
                            continue;
                        break loop;
                    default:
                        break loop;
                }
            }
        }

        @Override
        public void thinkOfHoward()
        {
            loop: while(true)
            {
                State state=_state.get();
                switch(state)
                {
                    case SPOCK:
                        break loop;
                    default:
                        if (!_state.compareAndSet(state,State.SCISSORS))
                            continue;
                        break loop;
                }
            }
        }
        
        @Override
        public String toString()
        {
            return _state.get().toString();
        }
        
    }
    
    static class StateClass
    {
        final String _name;
        
        StateClass(String name)
        {
            _name=name;
        }
        
        StateClass playSheldon()
        {
            return LIZZARD;
        }
        
        StateClass seePenny()
        {
            return this;
        }
        
        StateClass thinkOfHoward()
        {
            return SCISSORS;
        }
        
        @Override
        public String toString()
        {
            return _name;
        }
    }

    static StateClass ROCK = new StateClass("ROCK")
    {
        
    };
    static StateClass PAPER = new StateClass("PAPER")
    {
        @Override
        StateClass playSheldon()
        {
            return LIZZARD;
        }
    };
    static StateClass SCISSORS = new StateClass("SCISSORS")
    {
        
    };
    static StateClass LIZZARD = new StateClass("LIZZARD")
    {
        @Override
        StateClass playSheldon()
        {
            return PAPER;
        }
        @Override
        StateClass seePenny()
        {
            return PAPER;
        }
    };
    static StateClass SPOCK = new StateClass("SPOCK")
    {
        @Override
        StateClass seePenny()
        {
            return ROCK;
        }
        
        @Override
        StateClass thinkOfHoward()
        {
            return SPOCK;
        }
    };
    
    
    static class PolymorphicMachine implements Machine
    {
        final AtomicReference<StateClass> _state = new AtomicReference<>(SPOCK);
        @Override
        public void playSheldon()
        {
            while(true)
            {
                StateClass state=_state.get();
                StateClass next=state.playSheldon();
                if (state==next || _state.compareAndSet(state,next))
                    break;
            }            
        }

        @Override
        public void seePenny()
        {
            while(true)
            {
                StateClass state=_state.get();
                StateClass next=state.seePenny();
                if (state==next || _state.compareAndSet(state,next))
                    break;
            }     
        }

        @Override
        public void thinkOfHoward()
        {
            while(true)
            {
                StateClass state=_state.get();
                StateClass next=state.thinkOfHoward();
                if (state==next || _state.compareAndSet(state,next))
                    break;
            }     
        }
    
        @Override 
        public String toString()
        {
            return _state.get().toString();
        }
    }
}
