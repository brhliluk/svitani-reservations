package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.html.*

@Composable
fun IComponent.SeriesCard(series: EventSeries, onSignUpClick: () -> Unit) {
    val currentStrings by strings

    div(className = "indicator w-full") {

        span(className = "indicator-item badge badge-secondary font-bold shadow-md") {
            +currentStrings.course
        }

        div(className = "card card-bordered bg-base-100 shadow-sm w-full transition-all hover:shadow-md hover:border-primary/50") {
            div(className = "card-body p-6") {

                h3(className = "card-title text-xl font-bold text-base-content") {
                    +series.title
                }

                div(className = "flex gap-2 my-1") {
                    div(className = "badge badge-tertiary badge-outline text-xs gap-1") {
                        span(className = "icon-[heroicons--academic-cap] size-3")
                        +"${series.lessonCount} ${currentStrings.courseLessons}"
                    }
                    div(className = "badge badge-ghost text-xs gap-1") {
                        span(className = "icon-[heroicons--users] size-3")
                        +"Max ${series.capacity}"
                    }
                }

                div(className = "flex items-center gap-2 text-sm text-base-content/70 mt-2 bg-base-200/50 p-2 rounded-lg") {
                    span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                    span(className = "font-medium") {
                        +"${series.startDate} — ${series.endDate}"
                    }
                }

                p(className = "py-3 text-base-content/80 text-sm leading-relaxed") {
                    +series.description
                }

                div(className = "card-actions items-center justify-between mt-4 pt-4 border-t border-base-200") {
                    div(className = "flex flex-col") {
                        span(className = "text-xs text-base-content/60 font-medium uppercase tracking-wider") { "Price" }
                        span(className = "text-xl font-bold text-primary") {
                            "${series.price} Kč"
                        }
                    }

                    button(className = "btn btn-primary rounded-full px-6 shadow-sm") {
                        span(className = "icon-[heroicons--pencil-square] size-5")
                        +currentStrings.courseSignUp
                        onClick { onSignUpClick() }
                    }
                }
            }
        }
    }
}