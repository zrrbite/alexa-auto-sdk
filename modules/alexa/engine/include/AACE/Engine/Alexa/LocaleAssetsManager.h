/*
 * Copyright 2019-2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

#ifndef ALEXA_CLIENT_SDK_SAMPLEAPP_INCLUDE_SAMPLEAPP_LOCALEASSETSMANAGER_H_
#define ALEXA_CLIENT_SDK_SAMPLEAPP_INCLUDE_SAMPLEAPP_LOCALEASSETSMANAGER_H_

#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <unordered_set>

#include <acsdkShutdownManagerInterfaces/ShutdownNotifierInterface.h>
#include <AVSCommon/SDKInterfaces/LocaleAssetsManagerInterface.h>
#include <AVSCommon/Utils/RequiresShutdown.h>

namespace aace {
namespace engine {
namespace alexa {

/**
 * AVS Device SDK's implementation of an asset manager.
 *
 * This manager will use the @c AlexaClientSDKConfig.json to retrieve the supported locales. For devices with wake word
 * enabled this class will support "ALEXA" only.
 */
class LocaleAssetsManager
        : public alexaClientSDK::avsCommon::sdkInterfaces::LocaleAssetsManagerInterface
        , public alexaClientSDK::avsCommon::utils::RequiresShutdown {
public:
    /**
     * Create a LocaleAssetsManager object.
     *
     * @return A pointer to a new LocaleAssetsManager object if it succeeds; otherwise, @c nullptr.
     */
    static std::shared_ptr<LocaleAssetsManager> create(bool enableWakeWord);

    /// @name LocaleAssetsManagerInterface methods
    /// @{
    bool changeAssets(const Locales& locales, const WakeWords& wakeWords) override;
    void cancelOngoingChange() override;
    WakeWordsSets getDefaultSupportedWakeWords() const override;
    std::map<LanguageTag, WakeWordsSets> getLanguageSpecificWakeWords() const override;
    std::map<Locale, WakeWordsSets> getLocaleSpecificWakeWords() const override;
    WakeWordsSets getSupportedWakeWords(const Locale& locale) const override;
    std::set<Locale> getSupportedLocales() const override;
    LocaleCombinations getSupportedLocaleCombinations() const override;
    Locale getDefaultLocale() const override;
    void addLocaleAssetsObserver(
        const std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::LocaleAssetsObserverInterface>& observer)
        override;
    void removeLocaleAssetsObserver(
        const std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::LocaleAssetsObserverInterface>& observer)
        override;
    void onConfigurationChanged(const alexaClientSDK::avsCommon::avs::CapabilityConfiguration& configuration) override;
    void setEndpointRegistrationManager(
        const std::shared_ptr<
            alexaClientSDK::avsCommon::sdkInterfaces::endpoints::EndpointRegistrationManagerInterface>& manager)
        override;
    /// @}

    /// @name RequiresShutdown methods
    /// @{
    void doShutdown() override;
    /// @}
private:
    /**
     * Constructor.
     */
    LocaleAssetsManager();

    /**
     * Initialize the assets manager object.
     *
     * @param enableWakeWord Indicates whether wake words are enabled in this device or not.
     * @return @c true if it succeeds; otherwise, return @c false.
     */
    bool initialize(bool enableWakeWord);

    /// Set with the supported wake words. This object doesn't support different wake words per locale.
    WakeWordsSets m_supportedWakeWords;

    /// Set with the supported locales.
    std::set<Locale> m_supportedLocales;

    /// Vector with the supported localesCombinations.
    LocaleCombinations m_supportedLocalesCombinations;

    /// The default locale.
    Locale m_defaultLocale;

    /// Mutex to synchronize access to observers.
    std::mutex m_observersMutex;

    //// Members below are modified after initialization and hence should be synchronized.

    /// Set with observers.
    std::unordered_set<std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::LocaleAssetsObserverInterface>>
        m_observers;
};

}  // namespace alexa
}  // namespace engine
}  // namespace aace

#endif  // ALEXA_CLIENT_SDK_SAMPLEAPP_INCLUDE_SAMPLEAPP_LOCALEASSETSMANAGER_H_
