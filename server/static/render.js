// We manage everything with a class, just like we did with our websocket logic.
export class ChartRenderer
{
    constructor(canvasId)
    {
        const ctx = document.getElementById(canvasId).getContext('2d');
        Chart.defaults.color = '#a0a0a0';
        Chart.defaults.borderColor = 'rgba(255, 255, 255, 0.1)';
        Chart.defaults.font.family = '"Latin Modern Roman", serif';


        this.heartIcon = document.getElementById('heart-icon');
        this.bpmText = document.getElementById('current-bpm');


        this.hrChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Frequência Cardíaca (BPM)',
                    data: [],
                    borderColor: '#ff2a55',
                    backgroundColor: 'rgba(255, 42, 85, 0.15)',
                    borderWidth: 2,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 2,
                    pointHoverRadius: 6
                }]
            },
            options: {
                responsive: true,
                animation: {
                    duration: 400,
                    easing: 'easeOutCubic', // Nice animations :))
                    loop: false

                },
                scales: {
                    x: {
                    title: { display: true, text: 'Tempo (s)' },
                    grid: { color: 'rgba(255, 255, 255, 0.05)' }
                    },
                    y: {
                        title: { display: true, text: 'BPM' },
                        suggestedMin: 60,
                        suggestedMax: 180
                    }
                },
                plugins: {
                    zoom: { // Handles zooming in-and-out logic. TODO: Improve this, it's too clunky and janky.
                        pan: {
                            enabled: true,
                            mode: 'x',
                        },
                        zoom: {
                            wheel: {
                                enabled: true,
                            },
                            mode: 'x',
                        },
                        limits: {
                            x: {
                                minRange: 2, // Zoom in as far as to see a single point
                            }
                        }
                    }
                }
            }
        });
    }

    // Renders the received data.
    renderData(time, bpm, isRecovered = false)
    {
        console.log(`${isRecovered ? "(Received [RECOVERED])" : "(Received)"} Time: ${time}, BPM: ${bpm}`);
        this.hrChart.data.labels.push(time);
        this.hrChart.data.datasets[0].data.push(bpm);
        this.hrChart.update();

        this.updateHeartBeat(bpm);
    }

    // Updates heart image and text indicators.
    updateHeartBeat(bpm)
    {
        if(!this.heartIcon || !this.bpmText) return;

        this.bpmText.innerText = bpm;
        const duration = 60/bpm;
        this.heartIcon.style.animation = `heartbeat ${duration}s infinite` // infinite keeps the animation going forever
    }

    // Clears graph.
    clear()
    {
        this.hrChart.data.labels = [];
        this.hrChart.data.datasets[0].data = [];
        this.hrChart.resetZoom();
        this.hrChart.update();

        if(this.bpmText) this.bpmText.innerText = "--";
        if(this.heartIcon) this.heartIcon.style.animation = "none";
    }
}