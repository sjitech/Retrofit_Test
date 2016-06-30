import org.junit.Assert;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.HttpException;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("CodeBlock2Expr")
public class Retrofit_Test {

    public static interface SomeService {
        @GET("xxx/yyy")
        Observable<String> someApi(@Query("qqq") String qqq);
    }

    @Test
    public void test() throws Exception {

        RxJavaCallAdapterFactory originCallAdaptorFactory = RxJavaCallAdapterFactory.create();

        CallAdapter.Factory newCallAdaptorFactory = new CallAdapter.Factory() {
            @Override
            public CallAdapter<?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {

                CallAdapter<?> ca = originCallAdaptorFactory.get(returnType, annotations, retrofit);

                return new CallAdapter<Observable<?>>() {

                    @Override
                    public Type responseType() {
                        return ca.responseType();
                    }

                    int restRetryCount = 3;

                    @Override
                    public <R> Observable<?> adapt(Call<R> call) {
                        Observable<?> rx = (Observable<?>) ca.adapt(call);

                        return rx.retryWhen(errors -> errors.flatMap(error -> {
                            boolean needRetry = false;
                            if (restRetryCount >= 1) {
                                if (error instanceof IOException) {
                                    needRetry = true;
                                } else if (error instanceof HttpException) {
                                    if (((HttpException) error).code() > 400) { //please modify this condition
                                        needRetry = true;
                                    }
                                }
                            }

                            if (needRetry) {
                                println("******* retry *******");
                                restRetryCount--;
                                return Observable.just(null);
                            } else {
                                return Observable.error(error);
                            }
                        }));
                    }
                };
            }
        };


        SomeService serviceInvoker = new Retrofit.Builder()
                .baseUrl("http://some_web_site.com")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(newCallAdaptorFactory)
                .build()
                .create(SomeService.class);


        Observable<String> rx = serviceInvoker.someApi("oooops");
        rx.subscribe(
                result -> println(result),
                error -> println("error: " + error));


        assertOut("19:57:36.609 @main ******* retry *******");
        assertOut("19:57:36.612 @main ******* retry *******");
        assertOut("19:57:36.613 @main ******* retry *******");
        assertOut("19:57:36.635 @main error: java.net.UnknownHostException: some_web_site.com");
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    //some log/assert utility


    private LinkedBlockingQueue<String> outQueue = new LinkedBlockingQueue<>();

    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private void println(String text) {
        text = LocalTime.now().format(timeFormatter) + " @" + Thread.currentThread().getName() + " " + text;
        outQueue.offer(text);
        System.out.println(text);
    }

    private String popLine() throws InterruptedException {
        return outQueue.take();
    }

    private void assertOut(String exceptedStr) throws InterruptedException {
        Assert.assertEquals(exceptedStr.substring(12), popLine().substring(12));
    }
}
