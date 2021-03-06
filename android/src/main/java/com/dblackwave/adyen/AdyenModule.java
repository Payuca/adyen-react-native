package com.dblackwave.adyen;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.adyen.checkout.core.AdditionalDetails;
import com.adyen.checkout.core.CheckoutException;
import com.adyen.checkout.core.NetworkingState;
import com.adyen.checkout.core.Observer;
import com.adyen.checkout.core.PaymentHandler;
import com.adyen.checkout.core.PaymentMethodHandler;
import com.adyen.checkout.core.PaymentReference;
import com.adyen.checkout.core.PaymentResult;
import com.adyen.checkout.core.RedirectDetails;
import com.adyen.checkout.core.StartPaymentParameters;
import com.adyen.checkout.core.handler.AdditionalDetailsHandler;
import com.adyen.checkout.core.handler.ErrorHandler;
import com.adyen.checkout.core.handler.RedirectHandler;
import com.adyen.checkout.core.handler.StartPaymentParametersHandler;
import com.adyen.checkout.core.model.PaymentMethod;
import com.adyen.checkout.core.model.PaymentSession;
import com.adyen.checkout.ui.CheckoutController;
import com.adyen.checkout.ui.CheckoutSetupParameters;
import com.adyen.checkout.ui.CheckoutSetupParametersHandler;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

public class AdyenModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private PaymentMethodHandler paymentMethodHandler;

    private PaymentReference paymentReference;

    private PaymentHandler mPaymentHandler;

    private Callback mPaymentResult;

    private Callback mPaymentException;

    private Callback mObserverNetworkingState;

    private Callback mObserverPaymentSession;

    private Callback mObserverPaymentResult;

    private Callback mObserverRedirectDetails;

    private Callback mObserverAdditionalDetails;

    private Callback mObserverException;

    private static final int REQUEST_CODE_CHECKOUT = 1;

    public AdyenModule(ReactApplicationContext context) {
        super(context);
        this.getReactApplicationContext().addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "Adyen";
    }

    @ReactMethod
    public void onObserverNetworkingState(Callback mObserverNetworkingState) {
        this.mObserverNetworkingState = mObserverNetworkingState;
    }

    @ReactMethod
    public void onObserverPaymentSession(Callback mObserverPaymentSession) {
        this.mObserverPaymentSession = mObserverPaymentSession;
    }

    @ReactMethod
    public void onObserverPaymentResult(Callback mObserverPaymentResult) {
        this.mObserverPaymentResult = mObserverPaymentResult;
    }

    @ReactMethod
    public void onObserverRedirectDetails(Callback mObserverRedirectDetails) {
        this.mObserverRedirectDetails = mObserverRedirectDetails;
    }

    @ReactMethod
    public void onObserverAdditionalDetails(Callback mObserverAdditionalDetails) {
        this.mObserverAdditionalDetails = mObserverAdditionalDetails;
    }

    @ReactMethod
    public void onObserverException(Callback mObserverException) {
        this.mObserverException = mObserverException;
    }

    @ReactMethod
    public void startPayment(final Callback requestPaymentSession, final Callback onErrorCallback) {
        CheckoutController.startPayment(getCurrentActivity(), new CheckoutSetupParametersHandler() {
            @Override
            public void onRequestPaymentSession(@NonNull CheckoutSetupParameters checkoutSetupParameters) {
                Log.d("Debug", "Request payment session");
                requestPaymentSession.invoke(
                        checkoutSetupParameters.getSdkToken(),
                        checkoutSetupParameters.getReturnUrl()
                );
            }

            @Override
            public void onError(@NonNull CheckoutException error) {
                onErrorCallback.invoke(error.getMessage());
                Log.d("Debug", error.getMessage());
            }
        });
    }

    @ReactMethod
    public void confirmPayment(final String encodedPaymentSession, final Callback onPaymentResult, final Callback onPaymentException) {
        mPaymentResult = onPaymentResult;
        mPaymentException = onPaymentException;
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    CheckoutController.handlePaymentSessionResponse(getCurrentActivity(), encodedPaymentSession, new StartPaymentParametersHandler() {
                        @Override
                        public void onPaymentInitialized(@NonNull StartPaymentParameters startPaymentParameters) {
                            paymentMethodHandler = CheckoutController.getCheckoutHandler(startPaymentParameters);
                            paymentMethodHandler.handlePaymentMethodDetails(getCurrentActivity(), REQUEST_CODE_CHECKOUT);
                        }

                        @Override
                        public void onError(@NonNull CheckoutException error) {
                            Log.d("CheckoutException", error.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.d("Exception", e.toString());
                }
            }
        });
    }

    @ReactMethod
    public void createPaymentSession(final String encodedPaymentSession, final Callback onReadyForPayment) {
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    CheckoutController.handlePaymentSessionResponse(getCurrentActivity(), encodedPaymentSession, new StartPaymentParametersHandler() {
                        @Override
                        public void onPaymentInitialized(@NonNull StartPaymentParameters startPaymentParameters) {
                            paymentReference = startPaymentParameters.getPaymentReference();
                            mPaymentHandler = paymentReference.getPaymentHandler(getCurrentActivity());
                            makeObservables();
                            WritableMap jsonPayment = preparePaymentSession(startPaymentParameters.getPaymentSession());
                            onReadyForPayment.invoke(jsonPayment);
                        }

                        @Override
                        public void onError(@NonNull CheckoutException error) {
                            Log.d("CheckoutException", error.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.d("Exception", e.toString());
                }
            }
        });
    }

    private WritableMap preparePaymentSession(PaymentSession paymentSession) {
        WritableMap jsonPayment = new WritableNativeMap();
        jsonPayment.putString("countryCode", paymentSession.getPayment().getCountryCode());

        WritableMap jsonAmount = new WritableNativeMap();
        jsonAmount.putInt("value", (int) paymentSession.getPayment().getAmount().getValue());
        jsonAmount.putString("currency", paymentSession.getPayment().getAmount().getCurrency());
        jsonPayment.putMap("amount", jsonAmount);

        WritableMap jsonPaymentSession = new WritableNativeMap();
        jsonPaymentSession.putMap("payment", jsonPayment);

        WritableArray jsonPaymentMethodsArray = new WritableNativeArray();
        for (PaymentMethod method : paymentSession.getPaymentMethods()) {
            WritableMap jsonPaymentMethods = new WritableNativeMap();
            jsonPaymentMethods.putString("name", method.getName());
            jsonPaymentMethods.putString("type", method.getType());
            jsonPaymentMethodsArray.pushMap(jsonPaymentMethods);
        }

        jsonPaymentSession.putArray("paymentMethods", jsonPaymentMethodsArray);
        if (paymentSession.getOneClickPaymentMethods() != null) {
            WritableArray jsonPaymentMethodsOneClickArray = new WritableNativeArray();
            for (PaymentMethod method : paymentSession.getOneClickPaymentMethods()) {
                WritableMap jsonPaymentMethodsOneClick = new WritableNativeMap();
                jsonPaymentMethodsOneClick.putString("name", method.getName());
                jsonPaymentMethodsOneClick.putString("type", method.getType());
                jsonPaymentMethodsOneClickArray.pushMap(jsonPaymentMethodsOneClick);
            }

            jsonPaymentSession.putArray("oneClickPaymentMethods", jsonPaymentMethodsOneClickArray);
        }

        return jsonPaymentSession;
    }

    private void makeObservables() {
        mPaymentHandler.getNetworkingStateObservable().observe(getCurrentActivity(), new Observer<NetworkingState>() {
            @Override
            public void onChanged(@NonNull NetworkingState networkingState) {
                mObserverNetworkingState.invoke(networkingState.isExecutingRequests());
            }
        });

        mPaymentHandler.getPaymentSessionObservable().observe(getCurrentActivity(), new Observer<PaymentSession>() {
            @Override
            public void onChanged(@NonNull PaymentSession paymentSession) {
                if (mObserverPaymentSession != null) {
                    WritableMap jsonPayment = preparePaymentSession(paymentSession);

                    mObserverPaymentSession.invoke(jsonPayment);
                }
            }
        });

        mPaymentHandler.getPaymentResultObservable().observe(getCurrentActivity(), new Observer<PaymentResult>() {
            @Override
            public void onChanged(@NonNull PaymentResult paymentResult) {
                if (mObserverPaymentResult != null) {
                    mObserverPaymentResult.invoke(paymentResult.getPayload(), paymentResult.getResultCode().toString());
                }
            }
        });

        mPaymentHandler.setRedirectHandler(getCurrentActivity(), new RedirectHandler() {
            @Override
            public void onRedirectRequired(@NonNull RedirectDetails redirectDetails) {
                if (mObserverRedirectDetails != null) {
                    mObserverRedirectDetails.invoke(redirectDetails.getUri().toString());
                }
            }
        });

        mPaymentHandler.setAdditionalDetailsHandler(getCurrentActivity(), new AdditionalDetailsHandler() {
            @Override
            public void onAdditionalDetailsRequired(@NonNull AdditionalDetails additionalDetails) {
                if (mObserverAdditionalDetails != null) {
                    mObserverAdditionalDetails.invoke(additionalDetails.getPaymentMethodType(), additionalDetails.getInputDetails().toArray());
                }
            }
        });

        mPaymentHandler.setErrorHandler(getCurrentActivity(), new ErrorHandler() {
            @Override
            public void onError(@NonNull CheckoutException error) {
                if (mObserverException != null) {
                    mObserverException.invoke(error.getMessage());
                }
            }
        });
    }

    @Override
    public void onNewIntent(final Intent Intent) {
    }

    @Override
    public void onActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_CODE_CHECKOUT) {
            if (resultCode == PaymentMethodHandler.RESULT_CODE_OK) {
                PaymentResult paymentResult = PaymentMethodHandler.Util.getPaymentResult(data);
                mPaymentResult.invoke(paymentResult.getPayload());
            } else {
                CheckoutException checkoutException = PaymentMethodHandler.Util.getCheckoutException(data);
                mPaymentException.invoke(resultCode, "exception");
            }
        }
    }
}